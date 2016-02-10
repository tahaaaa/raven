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

  implicit val logger: LoggingAdapter = system.log
  implicit val timeout: Timeout = t

  import com.opentok.raven.http.JsonProtocol._

  def fillInEmail(e: Email): Email = e.copy(id = Some(UUID.randomUUID.toString))

  val route: Route =
    post {
      path("certified") {
        pathEndOrSingleSlash {
          entity(as[Requestable]) {
            case req: EmailRequest ⇒ handler.ask(EmailRequest.fillInRequest(req).validated).mapTo[Receipt]
            case em: Email ⇒ handler.ask(fillInEmail(em)).mapTo[Receipt]
          }
        }
      }
    }
}