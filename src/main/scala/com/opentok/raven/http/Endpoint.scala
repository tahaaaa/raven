package com.opentok.raven.http

import akka.event.LoggingAdapter
import akka.http.scaladsl.server.Route

trait Endpoint extends JsonProtocols {

  val route: Route

}
