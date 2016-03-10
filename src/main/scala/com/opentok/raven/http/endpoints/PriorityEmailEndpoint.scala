package com.opentok.raven.http.endpoints

import akka.actor.{ActorRef, ActorSystem}
import akka.event.LoggingAdapter
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.stream.Materializer
import akka.util.Timeout
import com.opentok.raven.http.EndpointUtils
import com.opentok.raven.model.{EmailRequest, Receipt}

import scala.util.Success

class PriorityEmailEndpoint(handler: ActorRef, t: Timeout)(implicit val mat: Materializer, system: ActorSystem) extends EndpointUtils {

  import com.opentok.raven.http.JsonProtocol._
  import system.dispatcher

  implicit val timeout: Timeout = t
  implicit val log: LoggingAdapter = system.log

  val route: Route =
    post {
      path("priority") {
        pathEndOrSingleSlash {
          entity(as[EmailRequest]) { req ⇒
            val validated = EmailRequest.fillInRequest(req).validated
            handler.ask(validated).mapTo[Receipt].andThen {
              case Success(r) if r.success ⇒
                log.info(s"email request with id '${validated.id.get}' template '${validated.template_id}' was sent successfully to '${validated.to}'")
              case _ ⇒
                log.warning(s"email request with id '${validated.id.get}' template '${validated.template_id}' failed to send to '${validated.to}'")
            }
          }
        }
      }
    }
}
