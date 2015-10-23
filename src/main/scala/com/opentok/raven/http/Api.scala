package com.opentok.raven.http

import akka.http.scaladsl.model.MediaTypes._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.util.CompactByteString
import com.opentok.raven.RavenConfig
import com.opentok.raven.http.endpoints.{DebugEndpoint, CertifiedEmailEndpoint, MonitoringEndpoint, PriorityEmailEndpoint}
import com.opentok.raven.model.Receipt
import com.opentok.raven.service.Service

trait Api {
  val routeTree: Route
}

/**
 * The REST API layer. It exposes the REST services, but does not provide any
 * web server interface.
 *
 * Notice that it requires to be mixed in with ``Service`` which provides access
 * to the top-level actors that make up the system.
 */
trait AkkaApi extends Api {
  this: com.opentok.raven.service.System with Service with RavenConfig ⇒

  def completeWithMessage(msg: String, rejection: Rejection) = {
    complete(HttpResponse(BadRequest,
      entity = HttpEntity.Strict(ContentType(`application/json`),
        CompactByteString(Receipt.receiptJsonFormat.write(
          Receipt.error(new Exception(s"${rejection.toString}"), msg)(system.log)).toString))))
  }

  val rejectionHandler = RejectionHandler.newBuilder().handle {
    case rej@MalformedRequestContentRejection(msg, _) ⇒
      completeWithMessage("There was a problem when unmarshalling body: " + msg, rej)
    case rej: Rejection ⇒ completeWithMessage("Oops!", rej)
  }
  val receiptExceptionHandler = ExceptionHandler {
    case e: Exception ⇒ complete(HttpResponse(InternalServerError,
      entity = HttpEntity.Strict(ContentType(`application/json`),
        CompactByteString(Receipt.receiptJsonFormat.write(
          Receipt.error(e, "Oops! There was an unexpected Error")(system.log)).toString()))))
  }

  val certified = new CertifiedEmailEndpoint(certifiedService, ENDPOINT_TIMEOUT)
  val priority = new PriorityEmailEndpoint(priorityService, ENDPOINT_TIMEOUT)

  val monitoring = new MonitoringEndpoint(monitoringService, ENDPOINT_TIMEOUT)

  val debugging = new DebugEndpoint

  val customRoutingSettings = RoutingSetup(
    rejectionHandler = rejectionHandler.result(),
    exceptionHandler = receiptExceptionHandler,
    materializer = materializer, routingLog = RoutingLog(system.log),
    routingSettings = RoutingSettings.default)

  val routeTree = logRequest("raven") {
    Route.seal(pathPrefix("v1") {
      handleExceptions(receiptExceptionHandler) {
        priority.route ~ certified.route ~ monitoring.route ~ debugging.route
      }
    })(customRoutingSettings)
  }
}