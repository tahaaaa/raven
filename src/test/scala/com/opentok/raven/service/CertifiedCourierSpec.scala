package com.opentok.raven.service

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import akka.util.Timeout
import com.opentok.raven.fixture._
import com.opentok.raven.model.{EmailRequest, Provider, Receipt}
import com.opentok.raven.service.actors.CertifiedCourier
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._
import scala.reflect.ClassTag

class CertifiedCourierSpec() extends TestKit(ActorSystem("CertifiedCourierSpec"))
with WordSpecLike with Matchers with BeforeAndAfterAll with ImplicitSender {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  implicit val ctx = system.dispatcher
  implicit val log = system.log

  def certifiedCourier(mockRequestDao: MockEmailRequestDao,
                       provider: Provider) =
    TestActorRef(Props(classOf[CertifiedCourier],
      mockRequestDao, provider, 500.millis: Timeout))

  "A Certified Courier" must {

    "First attempt to persist request to db and then pass it to sendgridService" in {
      val dao = new MockEmailRequestDao(Some(testRequest))
      val serv = new MockProvider(Receipt.success)
      val courier = certifiedCourier(dao, serv)

      within(3.seconds) {
        courier ! testRequest

        expectMsg[Receipt](Receipt.success(None, testRequest.id))
      }

      serv.right shouldBe 1

      dao.received.head.status shouldBe Some(EmailRequest.Pending) //first save with status pending
      dao.received.tail.head.status shouldBe Some(EmailRequest.Succeeded) //then save success

    }

    "If first attempt to persist fails, try to send anyway but change success receipt message with warning" in {
      val dao = new MockEmailRequestDao(Some(testRequest), persistanceFails = true)
      val serv = new MockProvider(Receipt.success)
      val courier = certifiedCourier(dao, serv)

      val r = within(3.seconds) {
        courier ! testRequest

        expectMsgType[Receipt](implicitly[ClassTag[Receipt]])
      }
      r.success should be(true)
      r.message.get.contains("problem") should be(true)

      serv.right shouldBe 1
    }

    "If attempt to persist timeouts, try to send anyway but change success receipt message with warning" in {
      val dao = new MockEmailRequestDao(Some(testRequest), persistanceTimesOut = true)
      val serv = new MockProvider(Receipt.success)
      val courier = certifiedCourier(dao, serv)

      val r = within(3.seconds) {
        courier ! testRequest

        expectMsgType[Receipt](implicitly[ClassTag[Receipt]])
      }

      r.success should be(true)
      r.message.get.contains("problem") should be(true)

      serv.right shouldBe 1
    }

    "If sendgridService request timeouts, should persist failure to db and return an unsuccessful receipt" in {
      val dao = new MockEmailRequestDao(Some(testRequest))
      val serv = new UnresponsiveProvider
      val courier = certifiedCourier(dao, serv)

      val r = within(3.seconds) {
        courier ! testRequest

        expectMsgType[Receipt](implicitly[ClassTag[Receipt]])
      }

      r.success should be(false)
      r.errors.exists(_.toLowerCase.contains("timeout"))

      dao.received.length should be(2) //first and second try
      dao.received.head.status should be(Some(EmailRequest.Pending)) //first save with pending
      dao.received.tail.head.status should be(Some(EmailRequest.Failed)) //then save failed

      serv.received shouldBe 1
    }

    "If both sendgridService and db timeouts, should return an unsuccessful receipt with both errors" in {
      val dao = new MockEmailRequestDao(Some(testRequest), persistanceTimesOut = true)
      val serv = new UnresponsiveProvider
      val courier = certifiedCourier(dao, serv)

      val r = within(3.seconds) {
        courier ! testRequest

        expectMsgType[Receipt](implicitly[ClassTag[Receipt]])
      }

      r.success should be(false)
      r.errors.exists(_.toLowerCase.contains("timeout"))
      r.errors.exists(_.toLowerCase.contains("problem"))

      dao.received.length should be(2) //first and second try
      dao.received.head.status should be(Some(EmailRequest.Pending)) //first save with pending
      dao.received.tail.head.status should be(Some(EmailRequest.Failed)) //then save failed

      serv.received shouldBe 1
    }

    "If receipt is success but contains errors, request should be stored with status filtered" in {
      val dao = new MockEmailRequestDao(Some(testRequest))
      val rec = Receipt(success = true, requestId = testRequest.id, errors = List("oopsies"))
      val serv = new MockProvider(rec)
      val courier = certifiedCourier(dao, serv)

      within(3.seconds) {
        courier ! testRequest

        expectMsg[Receipt](rec)
      }

      serv.right shouldBe 1

      dao.received.head.status shouldBe Some(EmailRequest.Pending) //first save with status pending
      dao.received.tail.head.status shouldBe Some(EmailRequest.Filtered) //then save success

    }
  }

}
