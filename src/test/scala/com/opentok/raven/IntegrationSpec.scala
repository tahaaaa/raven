package com.opentok.raven

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.http.scaladsl.unmarshalling._
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.testkit.TestKit
import com.opentok.raven.fixture.{H2Dal, _}
import com.opentok.raven.http.{AkkaApi, JsonProtocols}
import com.opentok.raven.model.{EmailRequest, Receipt}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import spray.json.DefaultJsonProtocol

import scala.concurrent.Await
import scala.concurrent.duration._

//uses all components but DAL uses an in-memory DB
//and fake sendgrid actor
class IntegrationSpec extends TestKit(ActorSystem("IntegrationSpec"))
with com.opentok.raven.service.System with TestConfig
with WordSpecLike with Matchers with JsonProtocols
with BeforeAndAfterAll with H2Dal with TestAkkaSystem with AkkaApi
with SprayJsonSupport with DefaultJsonProtocol {

  //start service
  val binding = Await.result(Http().bindAndHandle(handler = routeTree,
    interface = HOST, port = PORT), 3.seconds)

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
    binding.unbind()
  }

  import system.dispatcher

  lazy val selfConnectionFlow: Flow[HttpRequest, HttpResponse, Any] =
    Http(system).outgoingConnection(HOST, PORT)

  implicit val routeTestTimeout = RouteTestTimeout(5.seconds)

  val receiptUnmarshaller: FromEntityUnmarshaller[Receipt] = Receipt.receiptJsonFormat
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

  "Expose in-flight emails information" in {
    val resp = Await.result(Source.single(RequestBuilding.Get("/v1/monitoring/pending"))
      .via(selfConnectionFlow).runWith(Sink.head), 3.seconds)
    resp.status.isSuccess() should be(true)
  }

  "Send an email via priority service, persist results to DB and reply back to requester with success" in {
    val receipt = Await.result(Source.single(RequestBuilding.Post("/v1/priority", marshalledRequest))
      .via(selfConnectionFlow).runWith(Sink.head).map(_.entity).flatMap(receiptUnmarshaller.apply), 3.seconds)

    smtpService.underlyingActor.wrong should be(0)
    smtpService.underlyingActor.right should be(1)

    Thread.sleep(2000) //wait for persist

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

    smtpService.underlyingActor.wrong should be(0)
    smtpService.underlyingActor.right should be(2)

    Thread.sleep(2000) //wait for persist

    val dbRecord = Await.result(emailRequestDao.retrieveRequest(receipt.requestId.get), 5.seconds).get
    dbRecord.id shouldBe receipt.requestId
    dbRecord.status shouldBe Some(EmailRequest.Succeeded)
    dbRecord.to shouldBe testRequest.to
    dbRecord.template_id shouldBe testRequest.template_id

    receipt.success shouldBe (true)
  }

  "Send a batch of emails via certified service, persist results to DB and reply back to requester with success" in {
    val receipt = Await.result(Source.single(RequestBuilding.Post("/v1/certified", marshalledBatch))
      .via(selfConnectionFlow).runWith(Sink.head).map(_.entity).flatMap(receiptUnmarshaller.apply), 3.seconds)

    Thread.sleep(2000)

    smtpService.underlyingActor.wrong should be(0)
    smtpService.underlyingActor.right should be(5)

    import driver.api._
    val count = Await.result(db.run(
      sql"SELECT status FROM email_requests WHERE recipient = 'ernest+ravenbatch@tokbox.com'".as[String]
    ), 5.seconds)
    count.length shouldBe nBatch
    receipt.success shouldBe (true)
    count.filter(_ == EmailRequest.Succeeded.toString.toLowerCase).length shouldBe nBatch

  }

  "Send an prebuilt email via certified service, persist results to DB and reply back to requester with success" in {
    val receipt = Await.result(Source.single(RequestBuilding.Post("/v1/certified", marshalledEmail))
      .via(selfConnectionFlow).runWith(Sink.head).map(_.entity).flatMap(receiptUnmarshaller.apply), 3.seconds)

    Thread.sleep(2000)

    smtpService.underlyingActor.wrong should be(0)
    smtpService.underlyingActor.right should be(6)

    val dbRecord = Await.result(emailRequestDao.retrieveRequest(receipt.requestId.get), 5.seconds).get
    dbRecord.id shouldBe receipt.requestId
    dbRecord.status shouldBe Some(EmailRequest.Succeeded)
    dbRecord.to shouldBe testEmail.get.to
    dbRecord.template_id shouldBe testEmail.get.fromTemplateId.get

    receipt.success shouldBe (true)
  }

  "Send a batch of prebuilt emails via certified service, persist results to DB and reply back to requester with success" in {
    val receipt = Await.result(Source.single(RequestBuilding.Post("/v1/certified", marshalledBatchEmail))
      .via(selfConnectionFlow).runWith(Sink.head).map(_.entity).flatMap(receiptUnmarshaller.apply), 3.seconds)

    Thread.sleep(2000)

    smtpService.underlyingActor.wrong should be(0)
    smtpService.underlyingActor.right should be(9)

    import driver.api._
    val count = Await.result(db.run(
      sql"SELECT status FROM email_requests WHERE recipient = 'BATCH@tokbox.com'".as[String]
    ), 5.seconds)
    count.length shouldBe nBatch
    count.filter(_ == EmailRequest.Succeeded.toString.toLowerCase).length shouldBe nBatch

    receipt.success shouldBe (true)
  }


}
