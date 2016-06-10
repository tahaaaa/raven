package com.opentok.raven.http.endpoints

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.FromRequestUnmarshaller
import akka.pattern.ask
import akka.stream.Materializer
import akka.util.Timeout
import build.unstable.tylog.Variation
import com.opentok.raven.RavenLogging
import com.opentok.raven.http.EndpointUtils
import com.opentok.raven.model.{Receipt, Requestable}

import scala.util.{Failure, Success}

/**
 * Both certified and priority endpoints use this class. Their differences are materialized
 * with the class constructor parameters.
 *
 * @param endpoint path to match against
 * @param handler actor instance that will handle requests
 * @param timeout timeout on requests to handler
 * @param um determines what entities can be unmarshalled on this endpoin
 */
class EmailEndpoint[T <: Requestable](endpoint: String, handler: ActorRef, timeout: Timeout, um: FromRequestUnmarshaller[T])
                                     (implicit val mat: Materializer, val system: ActorSystem)
  extends EndpointUtils with RavenLogging {

  import system.dispatcher

  val callType = HandleEmailRequest(endpoint)

  val route: Route =
    post {
      path(endpoint) {
        pathEndOrSingleSlash {
          extractRequestContext(um) { ctx ⇒

            val requestId = ctx.req.id.get
            val traceId = ctx.traceId
            lazy val recipients = ctx.req.recipients

            trace(log, traceId, callType, Variation.Attempt, "extracted request with id {}", requestId)

            handler.ask(ctx)(timeout).mapTo[Receipt].andThen {

              case Success(r) if r.success ⇒
                trace(log, ctx.traceId, callType, Variation.Success,
                  "email request with id '{}' was sent successfully to '{}'",
                  requestId, recipients)

              case Success(r) ⇒
                trace(log, ctx.traceId, callType,
                  Variation.Failure(new Exception(r.errors.headOption.getOrElse("unknown error"))),
                  "email request with id '{}' failed to send to '{}'",
                  requestId, recipients)

              case Failure(e) ⇒
                trace(log, ctx.traceId, callType, Variation.Failure(e),
                  "email request with id '{}' failed to send to '{}'",
                  requestId, recipients)
            }
          }
        }
      }
    }
}
