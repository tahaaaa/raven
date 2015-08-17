package com.opentok.raven.http.endpoints

import akka.actor.{ActorSystem, ActorRef}
import akka.event.LoggingAdapter
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import com.opentok.raven.http.{Endpoint, JsonProtocols}

class PriorityEmailEndpoint(handler: ActorRef)(implicit val mat: Materializer, system: ActorSystem) extends Endpoint {

  implicit val logger: LoggingAdapter = system.log

  val route: Route = pathPrefix("priority") {
    get {
      path("id" / LongNumber) { wfId ⇒
        complete("OK")
      }
    } ~
      post {
        complete("OK")
      } ~ delete {
      getFromResource("")
    }
  }
}
