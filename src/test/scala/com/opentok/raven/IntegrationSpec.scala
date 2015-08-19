package com.opentok.raven

import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import com.opentok.raven.fixture.H2Dal
import com.opentok.raven.http.{AkkaApi, JsonProtocols}
import com.opentok.raven.model.{EmailRequest, Receipt}
import com.opentok.raven.service.AkkaSystem
import org.joda.time.DateTime
import org.scalatest.{Matchers, WordSpec}
import spray.json._

import scala.concurrent.duration._

class IntegrationSpec extends WordSpec with Matchers with ScalatestRouteTest with JsonProtocols {

  implicit val routeTestTimeout = RouteTestTimeout(3.seconds)

  //uses all components but DAL uses an in-memory DB
  val routeTree = (new H2Dal with AkkaSystem with AkkaApi).routeTree

  "Expose connectivity between service and database" in {
    Get("/v1/monitoring/health?component=dal") ~> routeTree ~> check {
      responseAs[Receipt].success shouldBe true
    }
  }

  "Expose service uptime check" in {
    Get("/v1/monitoring/health?component=service") ~> routeTree ~> check {
      responseAs[Receipt].success shouldBe true
    }
  }

  "Expose in-flight emails information" in {
    Get("/v1/monitoring/inflight") ~> routeTree ~> check {
      response.status.isSuccess()
    }
  }

  val testRequest = EmailRequest("ernest+raven@tokbox.com", "twirl_test",
    Some(JsObject(Map("a" → JsString(s"INTEGRATION TEST RUN AT ${new DateTime().toString}"),
      "b" → JsNumber(1)))), None, None)

  val marshalledRequest = EmailRequest.requestJsonFormat.write(testRequest)

  val marshalledBatch: JsValue = JsArray(Vector.fill(3)(EmailRequest.requestJsonFormat.write(testRequest)).toSeq: _*)

  "Send an email via priority service, persist results to DB and reply back to requester with success" in {
    Post("/v1/priority", marshalledRequest) ~> routeTree ~> check {
      responseAs[Receipt].success shouldBe true
    }
  }

  "Send an email via certified service, persist results to DB and reply back to requester with success" in {
    Post("/v1/certified", marshalledRequest) ~> routeTree ~> check {
      responseAs[Receipt].success shouldBe true
    }
  }

  "Send a batch of emails via certified service, persist results to DB and reply back to requester with success" in {
    Post("/v1/certified", marshalledBatch) ~> routeTree ~> check {
      responseAs[Receipt].success shouldBe true
    }
  }


}
