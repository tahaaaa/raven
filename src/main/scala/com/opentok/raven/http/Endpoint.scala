package com.opentok.raven.http

import akka.http.scaladsl.server.Route

trait Endpoint extends JsonProtocols {

  val route: Route

}
