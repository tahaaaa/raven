package com.opentok.raven

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.http.scaladsl.unmarshalling._
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.testkit.TestKit
import com.opentok.raven.fixture.{H2Dal, _}
import com.opentok.raven.http.AkkaApi
import com.opentok.raven.model.{EmailRequest, Receipt}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * uses all components but DAL uses an in-memory DB
 * and couriers have a mocked email provider that doesn't send emails
 */
class IntegrationSpec extends TestKit(ActorSystem("IntegrationSpec"))
with com.opentok.raven.service.System with TestConfig
with WordSpecLike with Matchers
with BeforeAndAfterAll with H2Dal with TestAkkaSystem with AkkaApi with RavenLogging{

  import com.opentok.raven.http.JsonProtocol._

  //start service
  val binding = Await.result(Http().bindAndHandle(handler = routeTree,
    interface = HOST, port = PORT), 3.seconds)

  override def afterAll() {
    binding.unbind()
    TestKit.shutdownActorSystem(system)
    db.close()
  }

  import system.dispatcher

  lazy val selfConnectionFlow: Flow[HttpRequest, HttpResponse, Any] =
    Http(system).outgoingConnection(HOST, PORT)

  implicit val routeTestTimeout = RouteTestTimeout(5.seconds)

  val receiptUnmarshaller: FromEntityUnmarshaller[Receipt] = receiptJsonFormat
  val mapUnmarshaller: FromEntityUnmarshaller[Map[String, Int]] = mapFormat[String, Int]

  "Expose connectivity between service and database" in {
    val receipt = Await.result(Source.single(RequestBuilding
      .Get("/v1/monitoring/health?component=dal")).via(selfConnectionFlow)
      .runWith(Sink.head).map(_.entity).flatMap(receiptUnmarshaller.apply), 3.seconds)
    receipt.success should be(true)
  }

  "Expose service uptime check" in {
    val receipt = Await.result(Source.single(RequestBuilding.Get("/v1/monitoring/health?component=service"))
      .via(selfConnectionFlow).runWith(Sink.head).map(_.entity).flatMap(receiptUnmarshaller.apply), 3.seconds)
    receipt.success should be(true)
  }

  "Send an email via priority service, persist results to DB and reply back to requester with success" in {
    val receipt = Await.result(Source.single(RequestBuilding.Post("/v1/priority", marshalledRequest))
      .via(selfConnectionFlow).runWith(Sink.head).map(_.entity).flatMap(receiptUnmarshaller.apply), 3.seconds)

    smtpProvider.right should be(1)

    val dbRecord = Await.result(emailRequestDao.retrieveRequest(receipt.requestId.get), 5.seconds).get
    dbRecord.id shouldBe receipt.requestId
    dbRecord.status shouldBe Some(EmailRequest.Succeeded)
    dbRecord.to shouldBe testRequest.to
    dbRecord.template_id shouldBe testRequest.template_id

    receipt.success shouldBe (true)
  }

  "Send an email via certified service, persist results to DB and reply back to requester with success" in {
    val receipt = Await.result(Source.single(RequestBuilding.Post("/v1/certified", marshalledRequest))
      .via(selfConnectionFlow).runWith(Sink.head).map(_.entity).flatMap(receiptUnmarshaller.apply), 3.seconds)

    smtpProvider.right should be(2) //prev 1 + 1

    val dbRecord = Await.result(emailRequestDao.retrieveRequest(receipt.requestId.get), 5.seconds).get
    dbRecord.id shouldBe receipt.requestId
    dbRecord.status shouldBe Some(EmailRequest.Succeeded)
    dbRecord.to shouldBe testRequest.to
    dbRecord.template_id shouldBe testRequest.template_id

    receipt.success shouldBe (true)
  }

  "Send a prebuilt email via certified service, persist results to DB and reply back to requester with success" in {
    val receipt = Await.result(Source.single(RequestBuilding.Post("/v1/certified", marshalledEmail))
      .via(selfConnectionFlow).runWith(Sink.head).map(_.entity).flatMap(receiptUnmarshaller.apply), 3.seconds)

    smtpProvider.right should be(3) //prev 2 + 1

    val dbRecord = Await.result(emailRequestDao.retrieveRequest(receipt.requestId.get), 5.seconds).get
    dbRecord.id shouldBe receipt.requestId
    dbRecord.status shouldBe Some(EmailRequest.Succeeded)
    dbRecord.to shouldBe testEmail.get.recipients.head
    dbRecord.template_id shouldBe testEmail.get.fromTemplateId.get

    receipt.success shouldBe (true)
  }

  "Expose in-flight emails information" in {
    val resp = Await.result(Source.single(RequestBuilding.Get("/v1/monitoring/pending"))
      .via(selfConnectionFlow).runWith(Sink.head), 3.seconds)
    resp.status.isSuccess() should be(true)
  }
}
