package com.opentok.raven.http

import akka.http.scaladsl.model.MediaTypes._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.http.scaladsl.settings.RoutingSettings
import akka.util.CompactByteString
import com.opentok.raven.{RavenLogging, RavenConfig}
import com.opentok.raven.http.endpoints.{CertifiedEmailEndpoint, DebugEndpoint, MonitoringEndpoint, PriorityEmailEndpoint}
import com.opentok.raven.model.{RavenRejection, Receipt}
import com.opentok.raven.service.Service

import scala.util.Try

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
  this: com.opentok.raven.service.System with Service with RavenConfig with RavenLogging ⇒

  def completeWithMessage(msg: String, rejection: Rejection) = {
    complete(HttpResponse(BadRequest,
      entity = HttpEntity.Strict(ContentType(`application/json`),
        CompactByteString(JsonProtocol.receiptJsonFormat.write(
          Receipt.error(new Exception(s"${rejection.toString}"), msg)).toString()))))
  }

  val rejectionHandler = RejectionHandler.newBuilder().handle {
    case rej@ValidationRejection(msg, Some(cause)) ⇒
      warning(log, "rejected: {}", rej)
      complete {
        HttpResponse(
          status = BadRequest,
          entity = HttpEntity.Strict(ContentType(`application/json`),
            CompactByteString(JsonProtocol.receiptJsonFormat.write(
              Receipt(
                success = false,
                message = Some("rejected"),
                errors = Try(cause.getMessage :: cause.getCause.getMessage :: Nil)
                  .getOrElse(cause.getMessage :: Nil)
              )
            ).toString)))
      }
    case rej: Rejection ⇒
      warning(log, "rejected: {}", rej)
      completeWithMessage("rejected", rej)
  }

  val receiptExceptionHandler = ExceptionHandler {
    case e: RavenRejection ⇒
      reject(new ValidationRejection(e.getMessage, Some(e)))
    //rest of exceptions
    case e: Exception ⇒
      val msg = "unexpected error"
      log.error(msg, e)
      complete(HttpResponse(InternalServerError,
        entity = HttpEntity.Strict(ContentType(`application/json`),
          CompactByteString(JsonProtocol.receiptJsonFormat.write(
            Receipt.error(e, msg)).toString()))))
  }

  val certified = new CertifiedEmailEndpoint(certifiedService, ENDPOINT_TIMEOUT)
  val priority = new PriorityEmailEndpoint(priorityService, ENDPOINT_TIMEOUT)

  val monitoring = new MonitoringEndpoint(monitoringService, ENDPOINT_TIMEOUT)

  val debugging = new DebugEndpoint

  val routeTree = logRequest("raven") {
    Route.seal(pathPrefix("v1") {
      handleExceptions(receiptExceptionHandler) {
        priority.route ~ certified.route ~ monitoring.route ~ debugging.route
      }
    })(RoutingSettings.default, rejectionHandler = rejectionHandler.result(),
      exceptionHandler = receiptExceptionHandler)
  }

}
