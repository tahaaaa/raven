package com.opentok.raven.http.endpoints

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.stream.Materializer
import com.opentok.raven.GlobalConfig
import com.opentok.raven.http.Endpoint
import com.opentok.raven.model.{EmailRequest, Receipt}

class PriorityEmailEndpoint(handler: ActorRef)(implicit val mat: Materializer, system: ActorSystem) extends Endpoint {

  import GlobalConfig.ENDPOINT_TIMEOUT

  val route: Route =
    post {
      path("priority") {
        pathEndOrSingleSlash {
          entity(as[EmailRequest]) { req ⇒
            complete(handler.ask(EmailRequest.fillInRequest(req)).mapTo[Receipt])
          }
        }
      }
    }
}
