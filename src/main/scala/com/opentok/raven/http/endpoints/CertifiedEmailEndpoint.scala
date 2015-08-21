package com.opentok.raven.http.endpoints

import akka.actor.{ActorRef, ActorSystem}
import akka.event.LoggingAdapter
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.stream.Materializer
import akka.util.Timeout
import com.opentok.raven.http.Endpoint
import com.opentok.raven.model.{Email, Requestable, EmailRequest, Receipt}


class CertifiedEmailEndpoint(handler: ActorRef, t: Timeout)(implicit val mat: Materializer, system: ActorSystem) extends Endpoint {

  implicit val logger: LoggingAdapter = system.log
  implicit val timeout: Timeout = t

  val route: Route =
    post {
      path("certified") {
        pathEndOrSingleSlash {
          entity(as[Either[List[Requestable], Requestable]]) {
            case Right(req: EmailRequest) ⇒ complete(handler.ask(EmailRequest.fillInRequest(req)).mapTo[Receipt])
            case Right(req: Email) ⇒ complete(handler.ask(Email.fillInEmail(req)).mapTo[Receipt])
            case Right(req) ⇒ complete(handler.ask(req).mapTo[Receipt])
            case Left(lReq) ⇒ complete(handler.ask(lReq.map {
              case e: EmailRequest ⇒ EmailRequest.fillInRequest(e)
              case e: Email ⇒ Email.fillInEmail(e)
              case e ⇒ e
            }).mapTo[Receipt])
          }
        }
      }
    }
}