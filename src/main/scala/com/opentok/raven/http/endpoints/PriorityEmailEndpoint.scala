package com.opentok.raven.http.endpoints

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.stream.Materializer
import akka.util.Timeout
import com.opentok.raven.RavenLogging
import com.opentok.raven.http.EndpointUtils
import com.opentok.raven.model.{EmailRequest, Receipt}

import scala.util.{Failure, Success}

class PriorityEmailEndpoint(handler: ActorRef, t: Timeout)(implicit val mat: Materializer, val system: ActorSystem)
  extends EndpointUtils with RavenLogging {

  import com.opentok.raven.http.JsonProtocol._
  import system.dispatcher

  implicit val timeout: Timeout = t

  val route: Route =
    post {
      path("priority") {
        pathEndOrSingleSlash {
          entity(as[EmailRequest]) { req ⇒
            val validated = EmailRequest.fillInRequest(req).validated
            trace(log, validated.id.get, PriorityEmailRequest, Variation.Attempt, None)
            handler.ask(validated).mapTo[Receipt].andThen {
              case Success(r) if r.success ⇒
                trace(log, validated.id.get, PriorityEmailRequest,
                  Variation.Success, Some(s"email request with id '${validated.id.get}' template '${validated.template_id}' was sent successfully to '${validated.to}'"))
              case Success(r) ⇒
                trace(log, validated.id.get, PriorityEmailRequest,
                  Variation.Failure(new Exception(r.errors.headOption.getOrElse("unknown error"))), Some(s"email request with id '${validated.id.get}' template '${validated.template_id}' failed to send to '${validated.to}'"))
              case Failure(e) ⇒
                trace(log, validated.id.get, PriorityEmailRequest,
                  Variation.Failure(e), Some(s"email request with id '${validated.id.get}' template '${validated.template_id}' failed to send to '${validated.to}'"))
            }
          }
        }
      }
    }
}
