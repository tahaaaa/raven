package com.opentok.raven.http.endpoints

import akka.actor.{ActorSystem, ActorRef}
import akka.event.LoggingAdapter
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.stream.Materializer
import com.opentok.raven.GlobalConfig
import com.opentok.raven.http.{Endpoint, JsonProtocols}
import com.opentok.raven.model.{EmailRequest, Receipt}
import com.opentok.raven.service.actors.EmailSupervisor


class CertifiedEmailEndpoint(handler: ActorRef)(implicit val mat: Materializer, system: ActorSystem) extends Endpoint {

  import GlobalConfig.DEFAULT_TIMEOUT

  implicit val logger: LoggingAdapter = system.log

  val route: Route = pathPrefix("certified") {
    post {
      path("send") {
        entity(as[EmailRequest]) { req ⇒
          complete {
            handler.ask(req).mapTo[Receipt]
          }
        }
      } ~
        path("send_batch") {
          entity(as[List[EmailRequest]]) { reqs ⇒
            complete {
              handler.ask(reqs).mapTo[Receipt]
            }
          }
        }
    }
  }
}