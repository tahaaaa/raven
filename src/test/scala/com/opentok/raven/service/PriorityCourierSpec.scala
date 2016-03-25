package com.opentok.raven.service

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import akka.util.Timeout
import com.opentok.raven.fixture._
import com.opentok.raven.model.{Provider, EmailRequest, Receipt}
import com.opentok.raven.service.actors.PriorityCourier
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._
import scala.reflect.ClassTag

class PriorityCourierSpec extends TestKit(ActorSystem("PriorityCourierSpec"))
with WordSpecLike with Matchers with BeforeAndAfterAll with ImplicitSender {

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  implicit val ctx = system.dispatcher
  implicit val log = system.log

  def priorityCourier(mockRequestDao: MockEmailRequestDao, provider: Provider) =
    system.actorOf(Props(classOf[PriorityCourier],
      mockRequestDao, provider, 1.seconds: Timeout))

  "A priority courier" must {
    "Bypass persistance and forward message to SendGrid, reply back with a successful receipt and persist results" in {
      val dao = new MockEmailRequestDao(Some(testRequest))
      val serv = new MockProvider(Receipt.success)
      val courier = priorityCourier(dao, serv)

      courier ! testRequest

      //receipt should contain receipt id
      expectMsg[Receipt](Receipt.success(None, testRequest.id))

      serv.right shouldBe 1
    }

    "Timeout/failing persist should not block forward to Sendgrid or replying success/failure to requester" in {
      val dao = new MockEmailRequestDao(Some(testRequest), persistanceFails = true)
      val serv = new MockProvider(Receipt.success)
      val courier = priorityCourier(dao, serv)

      courier ! testRequest
      //receipt should contain receipt id
      expectMsg[Receipt](2.seconds, Receipt.success(None, testRequest.id))

      serv.right shouldBe 1

      val dao2 = new MockEmailRequestDao(Some(testRequest), persistanceTimesOut = true)
      val courier2 = priorityCourier(dao2, serv)

      courier2 ! testRequest
      //receipt should contain receipt id
      expectMsg[Receipt](2.seconds, Receipt.success(None, testRequest.id))

      serv.right shouldBe 2
    }

    "If sendgridService request timeouts, should persist failure to db and return an unsuccessful receipt" in {
      val dao = new MockEmailRequestDao(Some(testRequest))
      val serv = new UnresponsiveProvider
      val courier = priorityCourier(dao, serv)
      courier ! testRequest

      val r = expectMsgType[Receipt](3.seconds)(implicitly[ClassTag[Receipt]])
      r.success should be(false)
      r.errors.exists(_.toLowerCase.contains("timeout"))

      dao.received.length should be(1) //only one save
      dao.received.head.status should be(Some(EmailRequest.Failed))

      serv.received shouldBe 1
    }

    "If both sendgridService and db timeouts, should return an unsuccessful receipt with both errors" in {
      val dao = new MockEmailRequestDao(Some(testRequest), persistanceTimesOut = true)
      val serv = new UnresponsiveProvider
      val courier = priorityCourier(dao, serv)
      courier ! testRequest

      val r = expectMsgType[Receipt](3.seconds)(implicitly[ClassTag[Receipt]])
      r.success should be(false)
      r.errors.exists(_.toLowerCase.contains("timeout"))
      r.errors.exists(_.toLowerCase.contains("problem"))

      dao.received.length should be(1) //only one save
      dao.received.head.status should be(Some(EmailRequest.Failed))

      serv.received shouldBe 1
    }

    "If receipt is success but contains errors, request should be stored with status filtered" in {
      val dao = new MockEmailRequestDao(Some(testRequest))
      val rec = Receipt(success = true, requestId = testRequest.id, errors = List("oopsies"))
      val serv = new MockProvider(rec)
      val courier = priorityCourier(dao, serv)

      within(3.seconds) {
        courier ! testRequest

        expectMsg[Receipt](rec)
      }

      serv.right shouldBe 1

      dao.received.head.status shouldBe Some(EmailRequest.Filtered)

    }
  }
}
