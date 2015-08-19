package com.opentok.raven.http.endpoints

import akka.actor.{ActorRef, ActorSystem}
import akka.event.LoggingAdapter
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.stream.Materializer
import com.opentok.raven.GlobalConfig
import com.opentok.raven.http.Endpoint
import com.opentok.raven.model.Receipt
import com.opentok.raven.service.actors.MonitoringActor

/**
 * Exposes status checks and stats about ongoing email deliveries
 * @param handler instance of [[com.opentok.raven.service.actors.MonitoringActor]]
 * @param mat ticktick
 */
class MonitoringEndpoint(handler: ActorRef)(implicit val mat: Materializer, system: ActorSystem) extends Endpoint {

  import GlobalConfig.ENDPOINT_TIMEOUT
  import mat.executionContext
  import spray.json.DefaultJsonProtocol._

  val actorNotReponding: PartialFunction[Throwable, Receipt] = {
    case e: Exception ⇒ Receipt.error(e, s"MonitoringActor in path ${handler.path} seems unresponsive", None)
  }

  implicit val logger: LoggingAdapter = system.log

  override val route: Route =
    get {
      pathPrefix("monitoring") {
        path("inflight") {
          complete {
            handler.ask(MonitoringActor.InFlightEmailsCheck)
              .mapTo[Map[String, Int]]
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
