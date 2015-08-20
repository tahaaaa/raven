package com.opentok.raven

import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import com.opentok.raven.fixture.H2Dal
import com.opentok.raven.http.{AkkaApi, JsonProtocols}
import com.opentok.raven.model.{EmailRequest, Receipt}
import com.opentok.raven.service.AkkaSystem
import com.typesafe.config.ConfigFactory
import org.joda.time.DateTime
import org.scalatest.{Matchers, WordSpec}
import spray.json._

import scala.concurrent.Await
import scala.concurrent.duration._

class IntegrationSpec extends WordSpec with Matchers with ScalatestRouteTest with JsonProtocols {

  implicit val routeTestTimeout = RouteTestTimeout(5.seconds)

  //uses all components but DAL uses an in-memory DB
  val app = new FromResourcesConfig(ConfigFactory.load()) with H2Dal with AkkaSystem with AkkaApi
  val routeTree = app.routeTree

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
      val receipt = responseAs[Receipt]
      receipt.success shouldBe true
      val dbRecord = Await.result(app.emailRequestDao.retrieveRequest(receipt.requestId.get), 5.seconds).get
      dbRecord.id shouldBe receipt.requestId
      dbRecord.status shouldBe Some(EmailRequest.Succeeded)
      dbRecord.to shouldBe testRequest.to
      dbRecord.template_id shouldBe testRequest.template_id
    }
  }

  "Send an email via certified service, persist results to DB and reply back to requester with success" in {
    Post("/v1/certified", marshalledRequest) ~> routeTree ~> check {
      val receipt = responseAs[Receipt]
      receipt.success shouldBe true
      val dbRecord = Await.result(app.emailRequestDao.retrieveRequest(receipt.requestId.get), 5.seconds).get
      dbRecord.id shouldBe receipt.requestId
      dbRecord.status shouldBe Some(EmailRequest.Succeeded)
      dbRecord.to shouldBe testRequest.to
      dbRecord.template_id shouldBe testRequest.template_id
    }
  }

  "Send a batch of emails via certified service, persist results to DB and reply back to requester with success" in {
    Post("/v1/certified", marshalledBatch) ~> routeTree ~> check {
      val receipt = responseAs[Receipt]
      receipt.success shouldBe true
    }
  }


}
