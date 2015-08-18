package com.opentok.raven.http.endpoints

import akka.actor.{ActorRef, ActorSystem}
import akka.event.LoggingAdapter
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import com.opentok.raven.http.Endpoint
import akka.pattern.ask
import com.opentok.raven.model.{EmailRequest, Receipt}

class PriorityEmailEndpoint(handler: ActorRef)(implicit val mat: Materializer, system: ActorSystem) extends Endpoint {

  implicit val logger: LoggingAdapter = system.log

  val route: Route = pathPrefix("priority") {
    post {
      path("send") {
        entity(as[EmailRequest]) { req ⇒
          complete {
            handler.ask(req).mapTo[Receipt]
          }
        }
      }
    }
  }
}
