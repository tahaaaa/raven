package com.opentok.raven.http.endpoints

import java.util.UUID

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.stream.Materializer
import akka.util.Timeout
import com.opentok.raven.RavenLogging
import com.opentok.raven.http.EndpointUtils
import com.opentok.raven.model.{Email, EmailRequest, Receipt, Requestable}

import scala.util.{Failure, Success}


class CertifiedEmailEndpoint(handler: ActorRef, t: Timeout)
                            (implicit val mat: Materializer, val system: ActorSystem)
  extends EndpointUtils with RavenLogging {

  implicit val timeout: Timeout = t

  import com.opentok.raven.http.JsonProtocol._
  import system.dispatcher

  def fillInEmail(e: Email): Email = e.id match {
    case Some(id) ⇒ e
    case None ⇒ e.copy(id = Some(UUID.randomUUID.toString))
  }

  val route: Route =
    post {
      path("certified") {
        pathEndOrSingleSlash {
          entity(as[Requestable]) {
            case req: EmailRequest ⇒
              val validated = EmailRequest.fillInRequest(req).validated
              trace(log, validated.id.get, CertifiedEmailRequest, Variation.Attempt, None)
              handler.ask(validated).mapTo[Receipt].andThen {
                case Success(r) if r.success ⇒
                  trace(log, validated.id.get, CertifiedEmailRequest,
                    Variation.Success, Some(s"email request with id '${validated.id.get}' template '${validated.template_id}' was sent successfully to '${validated.to}'"))
                case Success(r) ⇒
                  trace(log, validated.id.get, CertifiedEmailRequest,
                    Variation.Failure(new Exception(r.errors.headOption.getOrElse("unknown error"))), Some(s"email request with id '${validated.id.get}' failed to send to '${validated.to}'"))
                case Failure(e) ⇒
                  trace(log, validated.id.get, CertifiedEmailRequest,
                    Variation.Failure(e), Some(s"email request with id '${validated.id.get}' template '${validated.template_id}' failed to send to '${validated.to}'"))
              }
            case em: Email ⇒
              val email = fillInEmail(em)
              trace(log, email.id.get, CertifiedEmail, Variation.Attempt, None)
              handler.ask(email).mapTo[Receipt].andThen {
                case Success(r) if r.success ⇒
                  trace(log, email.id.get, CertifiedEmail, Variation.Success, Some(s"email with id '${email.id.get}' was sent successfully to '${email.recipients}'"))
                case Success(r) ⇒
                  trace(log, email.id.get, CertifiedEmail, Variation.Failure(new Exception(r.errors.headOption.getOrElse("unknown error"))), Some(s"email with id '${email.id.get}' failed to send to '${email.recipients}'"))
                case Failure(e) ⇒
                  trace(log, email.id.get, CertifiedEmail, Variation.Failure(e), Some(s"email with id '${email.id.get}' failed to send to '${email.recipients}'"))
              }
          }
        }
      }
    }
}