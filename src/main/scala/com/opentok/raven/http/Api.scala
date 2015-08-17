package com.opentok.raven.http

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.opentok.raven.http.endpoints.{MonitoringEndpoint, CertifiedEmailEndpoint, PriorityEmailEndpoint}

trait Api {
  val routeTree: Route
}

trait AkkaApi extends Api {
  this: com.opentok.raven.service.System ⇒

  val certified = new CertifiedEmailEndpoint(certifiedService)
  val priority = new PriorityEmailEndpoint(priorityService)

  val monitoring = new MonitoringEndpoint(monitoringService)

  val routeTree = logRequestResult("hermes") {
    Route.seal(pathPrefix("v1") {
      priority.route ~ certified.route ~ monitoring.route
    })
  }
}