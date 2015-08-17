package com.opentok.raven.http.endpoints

import akka.actor.{ActorSystem, ActorRef}
import akka.event.LoggingAdapter
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.stream.Materializer
import com.opentok.raven.GlobalConfig
import com.opentok.raven.http.{Endpoint, JsonProtocols}
import com.opentok.raven.model.Receipt
import com.opentok.raven.service.actors.EmailSupervisor


class CertifiedEmailEndpoint(handler: ActorRef)(implicit val mat: Materializer, system: ActorSystem) extends Endpoint {

  import GlobalConfig.DEFAULT_TIMEOUT

  implicit val logger: LoggingAdapter = system.log

  val route: Route = pathPrefix("certified") {
    post {
      path("send") {
        entity(as[EmailSupervisor.RelayEmailCmd]) { cmd ⇒
          complete {
            handler.ask(cmd).mapTo[Receipt]
          }
        }
      } ~
        path("sendBatch") {
          complete("OK")
        }
    }
  }
}