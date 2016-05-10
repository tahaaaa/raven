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

import scala.util.{Success, Failure}


class CertifiedEmailEndpoint(handler: ActorRef, t: Timeout)(implicit val mat: Materializer, system: ActorSystem) extends EndpointUtils {

  implicit val log: LoggingAdapter = system.log
  implicit val timeout: Timeout = t

  import com.opentok.raven.http.JsonProtocol._
  import system.dispatcher

  def fillInEmail(e: Email): Email = e.copy(id = Some(UUID.randomUUID.toString))

  val route: Route =
    post {
      path("certified") {
        pathEndOrSingleSlash {
          entity(as[Requestable]) {
            case req: EmailRequest ⇒
              val validated = EmailRequest.fillInRequest(req).validated
              handler.ask(validated).mapTo[Receipt].andThen {
                case Success(r) if r.success ⇒
                  log.info(s"email request with id '${validated.id.get}' template '${validated.template_id}' was sent successfully to '${validated.to}'")
                case _ ⇒
                  log.warning(s"email request with id '${validated.id.get}' template '${validated.template_id}' failed to send to '${validated.to}'")
              }
            case em: Email ⇒
              val email = fillInEmail(em)
              handler.ask(email).mapTo[Receipt].andThen {
                case Success(r) if r.success ⇒
                  log.info(s"email with id '${email.id.get}' was sent successfully to '${email.recipients}'")
                case _ ⇒
                  log.warning(s"email with id '${email.id.get}' failed to send to '${email.recipients}'")
              }
          }
        }
      }
    }
}