package com.opentok.raven.http.endpoints

import akka.actor.{ActorSystem, ActorRef}
import akka.event.LoggingAdapter
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import akka.http.scaladsl.server.Directives._
import com.opentok.raven.http.{Endpoint, JsonProtocols}
import akka.pattern.ask
import com.opentok.raven.model.Receipt
import com.opentok.raven.service.actors.MonitoringActor

import scala.concurrent.Future

/**
 * Exposes status checks and stats about ongoing email deliveries
 * @param handler instance of [[com.opentok.raven.service.actors.MonitoringActor]]
 * @param mat ticktick
 */
class MonitoringEndpoint(handler: ActorRef)(implicit val mat: Materializer, system: ActorSystem) extends Endpoint {

  import com.opentok.raven.GlobalConfig.DEFAULT_TIMEOUT
  import mat.executionContext

  implicit val logger: LoggingAdapter = system.log

  override val route: Route =
    pathPrefix("monitoring") {
      path("health") {
        get {
          parameters('component.as[String]) { component ⇒
            complete {
              handler.ask(MonitoringActor.ComponentHealthCheck(component)).mapTo[Receipt] recover {
                case e: Exception ⇒ Receipt.error(e, Some(s"MonitoringActor in path ${handler.path} seems unresponsive"))
              }
            }
          }
        } ~
          get {
            complete {
              Receipt.success(Some("OK"), None)
            }
          }
      }
    }
}
