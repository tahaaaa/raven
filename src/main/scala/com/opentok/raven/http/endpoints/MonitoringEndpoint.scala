package com.opentok.raven.http.endpoints

import akka.actor.{ActorRef, ActorSystem}
import akka.event.LoggingAdapter
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.stream.Materializer
import akka.util.Timeout
import com.opentok.raven.model.Receipt
import com.opentok.raven.service.actors.MonitoringActor

class MonitoringEndpoint(handler: ActorRef, t: Timeout)(implicit val mat: Materializer, system: ActorSystem) {

  import com.opentok.raven.http.JsonProtocol._
  import mat.executionContext

  implicit val timeout: Timeout = t

  val actorNotReponding: PartialFunction[Throwable, Receipt] = {
    case e: Exception ⇒ Receipt.error(e, s"MonitoringActor in path ${handler.path} seems unresponsive", None)
  }

  implicit val logger: LoggingAdapter = system.log

  val route: Route =
    get {
      pathPrefix("monitoring") {
        path("pending") {
          complete {
            handler.ask(MonitoringActor.FailedEmailsCheck).mapTo[Vector[Receipt]]
          }
        } ~
          path("health") {
            parameters('component.as[String]) { component ⇒
              complete {
                handler.ask(MonitoringActor.ComponentHealthCheck(component))
                  .mapTo[Receipt]
                  .recover(actorNotReponding)
              }
            } ~
              complete {
                Receipt.success(Some("OK"), None)
              }
          }
      }
    }
}
