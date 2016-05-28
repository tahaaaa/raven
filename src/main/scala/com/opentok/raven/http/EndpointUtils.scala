package com.opentok.raven.http

import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.model.MediaTypes._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.{ContentType, HttpEntity, HttpResponse}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive1, Route, RouteResult}
import akka.http.scaladsl.unmarshalling.FromRequestUnmarshaller
import akka.util.{CompactByteString, Timeout}
import build.unstable.tylog.Variation
import com.opentok.raven.RavenLogging
import com.opentok.raven.http.JsonProtocol._
import com.opentok.raven.model._

import scala.concurrent.Future
import scala.util.{Failure, Success}

trait EndpointUtils {
  this: RavenLogging ⇒

  implicit val timeout: Timeout
  val route: Route
  val system: ActorSystem

  def extractTraceHeader: Directive1[Option[String]] = {
    extractRequest.tflatMap {
      case Tuple1(req) ⇒
        provide(req.headers.find(_.name() == "X-TRACE").map(_.value()))
    }
  }

  def extractRequestContext[T <: Requestable](um: FromRequestUnmarshaller[T]): Directive1[RequestContext] = {

    extractTraceHeader.tflatMap { case Tuple1(traceIdMaybe) ⇒

      //generate request_id if traceId was extracted from header
      //otherwise generate one and use it for both
      val generatedId = UUID.randomUUID().toString
      val (requestId, traceId) = traceIdMaybe.map(t ⇒ t → generatedId).getOrElse(generatedId → generatedId)

      trace(log, traceId, CompleteRequest, Variation.Attempt, "")

      entity(um).tflatMap { case Tuple1(t) ⇒

        val req = t match {
          case r: EmailRequest ⇒
            r.copy(id = Some(requestId), status = Some(EmailRequest.Pending))
                    //validate that template exists and that the right parameters were passed
                    .validated
          case e: Email ⇒
            e.copy(id = Some(requestId))
        }

        mapRouteResultPF {
          case r: RouteResult.Complete ⇒
            trace(log, traceId, CompleteRequest, Variation.Success, "")
            r
          case r@RouteResult.Rejected(rejections) ⇒
            val e = new Exception(rejections.foldLeft("")((acc, r) ⇒ acc + "; " + r.toString))
            trace(log, traceId, CompleteRequest, Variation.Failure(e), "")
            r
        }.tflatMap { _ ⇒
          provide(RequestContext(req, traceId))
        }
      }
    }
  }

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
