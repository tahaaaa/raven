package com.opentok.raven.http.endpoints

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.stream.Materializer
import akka.util.Timeout
import build.unstable.tylog.Variation
import com.opentok.raven.RavenLogging
import com.opentok.raven.http.EndpointUtils
import com.opentok.raven.model.{Receipt, Requestable}

import scala.util.{Failure, Success}


class CertifiedEmailEndpoint(handler: ActorRef, t: Timeout)
                            (implicit val mat: Materializer, val system: ActorSystem)
  extends EndpointUtils with RavenLogging {

  implicit val timeout: Timeout = t

  import com.opentok.raven.http.JsonProtocol._
  import system.dispatcher

  val route: Route =
    post {
      path("certified") {
        pathEndOrSingleSlash {
          //entity can be Email or EmailRequest
          extractRequestContext(as[Requestable]) { ctx ⇒

            trace(log, ctx.traceId, HandleCertifiedEmailRequest, Variation.Attempt,
              "extracted request with id {}", ctx.req.id.get)

            val requestId = ctx.req.id.get

            handler.ask(ctx).mapTo[Receipt].andThen {

              case Success(r) if r.success ⇒
                trace(log, ctx.traceId, HandleCertifiedEmailRequest, Variation.Success,
                  "email request with id '{}' was sent successfully to '{}'",
                  requestId, ctx.req.recipients)

              case Success(r) ⇒
                trace(log, ctx.traceId, HandleCertifiedEmailRequest,
                  Variation.Failure(new Exception(r.errors.headOption.getOrElse("unknown error"))),
                  "email request with id '{}' failed to send to '{}'",
                  requestId, ctx.req.recipients)

              case Failure(e) ⇒
                trace(log, ctx.traceId, HandleCertifiedEmailRequest, Variation.Failure(e),
                  "email request with id '{}' failed to send to '{}'",
                  requestId, ctx.req.recipients)
            }
          }
        }
      }
    }
}
