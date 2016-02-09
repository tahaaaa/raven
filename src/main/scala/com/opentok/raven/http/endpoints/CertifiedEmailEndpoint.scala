package com.opentok.raven.http.endpoints

import java.util.UUID

import akka.actor.{ActorRef, ActorSystem}
import akka.event.LoggingAdapter
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.stream.Materializer
import akka.util.Timeout
import com.opentok.raven.http.EndpointUtils
import com.opentok.raven.model.{Email, EmailRequest, Receipt, Requestable}


class CertifiedEmailEndpoint(handler: ActorRef, t: Timeout)(implicit val mat: Materializer, system: ActorSystem) extends EndpointUtils {

  import com.opentok.raven.http.JsonProtocol._

  implicit val logger: LoggingAdapter = system.log
  implicit val timeout: Timeout = t

  def fillInEmail(e: Email): Email = e.copy(id = Some(UUID.randomUUID.toString))

  val route: Route =
    post {
      path("certified") {
        pathEndOrSingleSlash {
          entity(as[Either[List[Requestable], Requestable]]) {
            case Right(req: EmailRequest) ⇒ handler.ask(EmailRequest.fillInRequest(req).validated).mapTo[Receipt]
            case Right(em: Email) ⇒ handler.ask(fillInEmail(em)).mapTo[Receipt]
            case Right(req) ⇒ handler.ask(req).mapTo[Receipt]
            case Left(lReq) ⇒ handler.ask(lReq.map {
              case req: EmailRequest ⇒ EmailRequest.fillInRequest(req).validated
              case em: Email ⇒ fillInEmail(em)
            }).mapTo[Receipt]
          }
        }
      }
    }
}