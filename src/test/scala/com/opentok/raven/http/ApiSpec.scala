package com.opentok.raven.http

import akka.actor.{Actor, Props}
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import com.opentok.raven.fixture.{H2Dal, TestConfig, MockSystem, WorkingMockSystem}
import com.opentok.raven.model.{Email, EmailRequest, Receipt}
import org.joda.time.DateTime
import org.scalatest.{Matchers, WordSpec}
import spray.json._
import com.opentok.raven.fixture._

import scala.concurrent.duration._

class ApiSpec extends WordSpec with Matchers with ScalatestRouteTest with JsonProtocols {

  //uses mock system, so db is irrelevant in this test, but still needs to be mixed in
  val workingTree = (new WorkingMockSystem with TestConfig with H2Dal with AkkaApi).routeTree

  val treeWithIrresponsiveService = (new MockSystem(Props(new Actor{
    override def receive: Receive = {
      case _ ⇒ //does not reply at all
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
    testRequest.inject.get,testRequest.to :: Nil)

  val marshalledRequest = EmailRequest.requestJsonFormat.write(testRequest)

  val marshalledBatch: JsValue = JsArray(Vector.fill(3)(EmailRequest.requestJsonFormat.write(testRequest)).toSeq: _*)

  "Unmarshall email request successfully and pass it to priority service" in {
    Post("/v1/priority", marshalledRequest) ~> workingTree ~> check {
      responseAs[Receipt].success shouldBe true
    }
  }

  "Unmarshall email request successfully and pass it to certified service" in {
    Post("/v1/certified", marshalledRequest) ~> workingTree ~> check {
      responseAs[Receipt].success shouldBe true
    }
  }

  "Unmarshall js array of email request successfully and pass it to priority service" in {
    Post("/v1/certified", marshalledBatch) ~> workingTree ~> check {
      responseAs[Receipt].success shouldBe true
    }
  }

  "Unmarshall premade Email successfully and pass it to certified service" in {
    Post("/v1/certified", marshalledEmail) ~> workingTree ~> check {
      responseAs[Receipt].success shouldBe true
    }
  }

  "Unmarshall js array of premade Emails successfully and pass it to priority service" in {
    Post("/v1/certified", marshalledBatchEmail) ~> workingTree ~> check {
      responseAs[Receipt].success shouldBe true
    }
  }

  "Use custom exceptions handler and reply with marshalled receipts, even when the service timeouts due tu an internal problem" in {
    Post("/v1/certified", marshalledRequest) ~> treeWithIrresponsiveService ~> check {
      val response = responseAs[Receipt]
      response.success shouldBe false
      response.errors.exists(_.contains("AskTimeout"))
    }
  }

  "Use default rejections handler" in {
    Post("/v1/certified", JsString("OOPS")) ~> workingTree ~> check {
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

}
