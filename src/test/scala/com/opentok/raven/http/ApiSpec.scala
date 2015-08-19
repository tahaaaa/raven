package com.opentok.raven.http

import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.opentok.raven.fixture.{H2Dal, MockSystem}
import com.opentok.raven.model.{EmailRequest, Receipt}
import org.joda.time.DateTime
import org.scalatest.{Matchers, WordSpec}
import spray.json._

class ApiSpec extends WordSpec with Matchers with ScalatestRouteTest with JsonProtocols {

  //uses mock system, so db is irrelevant in this test, but still needs to be mixed in
  val routeTree = (new H2Dal with MockSystem with AkkaApi).routeTree

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

  val testRequest = EmailRequest("ernest+raven@tokbox.com", "twirl_test",
    Some(JsObject(Map("a" → JsString(s"API UNIT TEST RUN AT ${new DateTime().toString}"),
      "b" → JsNumber(2)))), None, None)

  val marshalledRequest = EmailRequest.requestJsonFormat.write(testRequest)

  val marshalledBatch: JsValue = JsArray(Vector.fill(3)(EmailRequest.requestJsonFormat.write(testRequest)).toSeq: _*)

  "Unmarshall email request successfully and pass it to priority service" in {
    Post("/v1/priority", marshalledRequest) ~> routeTree ~> check {
      responseAs[Receipt].success shouldBe true
    }
  }

  "Unmarshall email request successfully and pass it to certified service" in {
    Post("/v1/certified", marshalledRequest) ~> routeTree ~> check {
      responseAs[Receipt].success shouldBe true
    }
  }

  "Unmarshall js array of email request successfully and pass it to priority service" in {
    Post("/v1/certified", marshalledBatch) ~> routeTree ~> check {
      responseAs[Receipt].success shouldBe true
    }
  }



}
