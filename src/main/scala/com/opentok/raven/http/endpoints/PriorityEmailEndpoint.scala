package com.opentok.raven.http.endpoints

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.MediaTypes._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.{ContentType, HttpEntity, HttpResponse}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.stream.Materializer
import akka.util.{CompactByteString, Timeout}
import com.opentok.raven.http.Endpoint
import com.opentok.raven.model.{EmailRequest, Receipt}

import scala.util.Success

class PriorityEmailEndpoint(handler: ActorRef, t: Timeout)(implicit val mat: Materializer, system: ActorSystem) extends Endpoint {

  implicit val timeout: Timeout = t
  val route: Route =
    post {
      path("priority") {
        pathEndOrSingleSlash {
          entity(as[EmailRequest]) { req ⇒
            onComplete(handler.ask(EmailRequest.fillInRequest(req)).mapTo[Receipt]).tapply {
              case Tuple1(Success(rec)) if rec.success ⇒ complete(rec)
              case Tuple1(Success(rec)) ⇒ complete(HttpResponse(InternalServerError,
                entity = HttpEntity.Strict(ContentType(`application/json`),
                  CompactByteString(Receipt.receiptJsonFormat.write(rec).toString()))))
            }
          }
        }
      }
    }
}
