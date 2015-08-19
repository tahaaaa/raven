package com.opentok.raven.http

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.opentok.raven.{RavenConfig, FromResourcesConfig}
import com.opentok.raven.http.endpoints.{MonitoringEndpoint, CertifiedEmailEndpoint, PriorityEmailEndpoint}

trait Api {
  val routeTree: Route
}

trait AkkaApi extends Api {
  this: com.opentok.raven.service.System with RavenConfig ⇒

  val certified = new CertifiedEmailEndpoint(certifiedService, ENDPOINT_TIMEOUT)
  val priority = new PriorityEmailEndpoint(priorityService, ENDPOINT_TIMEOUT)

  val monitoring = new MonitoringEndpoint(monitoringService, ENDPOINT_TIMEOUT)

  val routeTree = logRequestResult("raven") {
    Route.seal(pathPrefix("v1") {
      priority.route ~ certified.route ~ monitoring.route
    })
  }
}