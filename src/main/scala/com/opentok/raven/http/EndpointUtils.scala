package com.opentok.raven.http

import akka.actor.ActorSystem
import akka.http.scaladsl.model.MediaTypes._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.{ContentType, HttpEntity, HttpResponse}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.{CompactByteString, Timeout}
import com.opentok.raven.http.JsonProtocol._
import com.opentok.raven.model.Receipt

import scala.concurrent.Future
import scala.util.{Failure, Success}

trait EndpointUtils {

  implicit val timeout: Timeout
  val route: Route
  val system: ActorSystem

  implicit def completeWithStatusCode(f: Future[Receipt]): Route = {
    onComplete(f).tapply {
      case Tuple1(Success(rec)) if rec.success ⇒ complete(rec)
      case Tuple1(Success(rec)) ⇒ complete(HttpResponse(BadGateway,
        entity = HttpEntity.Strict(ContentType(`application/json`),
          CompactByteString(receiptJsonFormat.write(rec).toString()))))
      case Tuple1(Failure(e)) ⇒ complete(HttpResponse(InternalServerError,
        entity = HttpEntity.Strict(ContentType(`application/json`),
          CompactByteString(receiptJsonFormat.write(Receipt.error(e, "unexpected error")).toString()))))
    }
  }

}
