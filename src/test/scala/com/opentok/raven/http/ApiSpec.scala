package com.opentok.raven.http

import akka.actor.{Actor, Props}
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.testkit.TestKit
import com.opentok.raven.fixture.{H2Dal, MockSystem, TestConfig, WorkingMockSystem, _}
import com.opentok.raven.model.{Requestable, Email, EmailRequest, Receipt}
import org.joda.time.DateTime
import org.scalatest.{Matchers, WordSpec}
import spray.json._
import JsonProtocol._

import scala.concurrent.duration._

class ApiSpec extends WordSpec with Matchers with ScalatestRouteTest {

  //uses mock system, so db is irrelevant in this test, but still needs to be mixed in
  val raven = new WorkingMockSystem with TestConfig with H2Dal with AkkaApi
  val workingTree = raven.routeTree


  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(raven.system)
    raven.db.close()
  }

  val treeWithIrresponsiveService = (new MockSystem(Props(new Actor {
    override def receive: Receive = {
      case _ ⇒ //does not reply at all
    }
  })) with TestConfig with H2Dal with AkkaApi).routeTree

  val treeWithIrresponsiveEmailProvider = (new MockSystem(Props(new Actor {
    override def receive: Receive = {
      case req: Requestable ⇒ sender() ! Receipt.error(new Exception("sendgrid irresponsive"), "oops")
      case batch: List[_] ⇒ sender() ! Receipt.error(new Exception("sendgrid irresponsive"), "oops")
    }
  })) with TestConfig with H2Dal with AkkaApi).routeTree

  implicit val routeTestTimeout = RouteTestTimeout(7.seconds)

  "Expose connectivity between service and database" in {
    Get("/v1/monitoring/health?component=dal") ~> workingTree ~> check {
      responseAs[Receipt].success shouldBe true
    }
  }

  "Expose service uptime check" in {
    Get("/v1/monitoring/health?component=service") ~> workingTree ~> check {
      responseAs[Receipt].success shouldBe true
    }
  }

  val testEmail = Email.build(testRequest.id, testRequest.template_id,
    testRequest.$inject, testRequest.to)

  "Unmarshall email request successfully and pass it to priority service" in {
    Post("/v1/priority", marshalledRequest) ~> workingTree ~> check {
      status.intValue() shouldBe 200
      responseAs[Receipt].success shouldBe true
    }
  }

  "Unmarshall email request successfully and pass it to certified service" in {
    Post("/v1/certified", marshalledRequest) ~> workingTree ~> check {
      status.intValue() shouldBe 200
      responseAs[Receipt].success shouldBe true
    }
  }

  "Unmarshall premade Email successfully and pass it to certified service" in {
    Post("/v1/certified", marshalledEmail) ~> workingTree ~> check {
      status.intValue() shouldBe 200
      responseAs[Receipt].success shouldBe true
    }
  }

  "Use custom exceptions handler and reply with marshalled receipts, even when the service timeouts due tu an internal problem" in {
    Post("/v1/certified", marshalledRequest) ~> treeWithIrresponsiveService ~> check {
      status.intValue() shouldBe 500
      val response = responseAs[Receipt]
      response.success shouldBe false
      response.errors.exists(_.contains("AskTimeout"))
    }
  }

  "Use default rejections handler" in {
    Post("/v1/certified", JsString("OOPS")) ~> workingTree ~> check {
      status.intValue() shouldBe 400
      response.status.isSuccess() shouldBe false
      response.status.value shouldBe "400 Bad Request"
    }
  }

  "Injects a request id if not found in incoming request" in {
    Post("/v1/priority", marshalledRequest) ~> workingTree ~> check {
      responseAs[Receipt].requestId should not be None
    }
    Post("/v1/certified", marshalledRequest) ~> workingTree ~> check {
      responseAs[Receipt].requestId should not be None
    }
    Post("/v1/certified", marshalledEmail) ~> workingTree ~> check {
      responseAs[Receipt].requestId should not be None
    }
  }

  "Reject request if a template_id is not valid" in {
    val invalidTemplateId = EmailRequest("ernest+raven@tokbox.com", "potato",
      Some(JsObject(Map("a" → JsString(s"INTEGRATION TEST RUN AT ${new DateTime().toString}"),
        "b" → JsNumber(1)))), None, Some("1"))

    val marshalledRequest = JsonProtocol.requestJsonFormat.write(invalidTemplateId)

    Post("/v1/priority", marshalledRequest) ~> workingTree ~> check {
      response.status.intValue() shouldBe 400
    }

    Post("/v1/certified", marshalledRequest) ~> workingTree ~> check {
      response.status.intValue() shouldBe 400
    }
  }

  "Reject request if missing injection parameters for a given template id" in {
    val missingInjections = EmailRequest("ernest+raven@tokbox.com", "test",
      Some(JsObject(Map("potatoes" → JsNull))), None, Some("1"))

    val marshalledRequest = JsonProtocol.requestJsonFormat.write(missingInjections)

    Post("/v1/priority", marshalledRequest) ~> workingTree ~> check {
      response.status.intValue() shouldBe 400
    }

    Post("/v1/certified", marshalledRequest) ~> workingTree ~> check {
      response.status.intValue() shouldBe 400
    }
  }

  "when requests passes validation but gateway fails status code should be 502 (Bad Gateway)" in {
    Post("/v1/priority", marshalledRequest) ~> treeWithIrresponsiveEmailProvider ~> check {
      response.status.intValue() shouldBe 502
      val rec = responseAs[Receipt]
      rec.success shouldBe false
    }

    Post("/v1/certified", marshalledRequest) ~> treeWithIrresponsiveEmailProvider ~> check {
      response.status.intValue() shouldBe 502
      val rec = responseAs[Receipt]
      rec.success shouldBe false
    }

    Post("/v1/certified", marshalledEmail) ~> treeWithIrresponsiveEmailProvider ~> check {
      response.status.intValue() shouldBe 502
      val rec = responseAs[Receipt]
      rec.success shouldBe false
    }
  }

  "should return bad request when trying to pass an email, email batch, or request batch through priority endpoint" in {

    Post("/v1/priority", marshalledEmail) ~> workingTree ~> check {
      response.status.intValue() shouldBe 400
      val rec = responseAs[Receipt]
      rec.success shouldBe false
    }
  }
}
