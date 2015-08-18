package com.opentok.raven.http.endpoints

import akka.actor.{ActorRef, ActorSystem}
import akka.event.LoggingAdapter
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.stream.Materializer
import com.opentok.raven.GlobalConfig
import com.opentok.raven.http.Endpoint
import com.opentok.raven.model.{EmailRequest, Receipt}


class CertifiedEmailEndpoint(handler: ActorRef)(implicit val mat: Materializer, system: ActorSystem) extends Endpoint {

  import GlobalConfig.ENDPOINT_TIMEOUT

  implicit val logger: LoggingAdapter = system.log

  val route: Route =
    post {
      path("certified") {
        pathEndOrSingleSlash {
          entity(as[Either[List[EmailRequest], EmailRequest]]) {
            case Right(req) ⇒ complete(handler.ask(EmailRequest.fillInRequest(req)).mapTo[Receipt])
            case Left(lReq) ⇒ complete(handler.ask(lReq.map(EmailRequest.fillInRequest)).mapTo[Receipt])
          }
        }
      }
    }
}