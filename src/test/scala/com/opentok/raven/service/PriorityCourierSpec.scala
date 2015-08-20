package com.opentok.raven.service

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import akka.util.Timeout
import com.opentok.raven.fixture._
import com.opentok.raven.model.{EmailRequest, Receipt}
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

  def priorityCourier(mockRequestDao: MockEmailRequestDao,
                      serv: TestActorRef[_]) =
    system.actorOf(Props(classOf[PriorityCourier],
      mockRequestDao, serv, 1.seconds: Timeout))

  "A priority courier" must {
    "Bypass persistance and forward message to SendGrid, reply back with a successful receipt and persist results" in {
      val dao = new MockEmailRequestDao(Some(testRequest))
      val serv = sendgridService
      val courier = priorityCourier(dao, serv)

      courier ! testRequest

      //receipt should contain receipt id
      expectMsg[Receipt](Receipt.success(None, testRequest.id))

      serv.underlyingActor.right shouldBe 1
      serv.underlyingActor.wrong shouldBe 0

      dao.received.length shouldBe 1
      dao.received.head.status shouldBe Some(EmailRequest.Succeeded)
    }

    "Timeout/failing persist should not block forward to Sendgrid or replying success/failure to requester" in {
      val dao = new MockEmailRequestDao(Some(testRequest), persistanceFails = true)
      val serv = sendgridService
      val courier = priorityCourier(dao, serv)

      courier ! testRequest
      //receipt should contain receipt id
      expectMsg[Receipt](2.seconds, Receipt.success(None, testRequest.id))

      serv.underlyingActor.right shouldBe 1
      serv.underlyingActor.wrong shouldBe 0

      val serv2 = sendgridService
      val dao2 = new MockEmailRequestDao(Some(testRequest), persistanceTimesOut = true)
      val courier2 = priorityCourier(dao2, serv2)

      courier2 ! testRequest
      //receipt should contain receipt id
      expectMsg[Receipt](2.seconds, Receipt.success(None, testRequest.id))

      serv2.underlyingActor.wrong shouldBe 0
      serv2.underlyingActor.right shouldBe 1
    }

    "If EmailRequest contains an invalid template, should reply with unsuccessful receipt and persist attempt" in {
      val testRequest2 = testRequest.copy(template_id = "¬…¬…¬…˚ø∆µ¬≤…¬")
      val dao = new MockEmailRequestDao(Some(testRequest2), persistanceFails = true)
      val serv = sendgridService
      val courier = priorityCourier(dao, serv)

      courier ! testRequest2
      //receipt should contain receipt id
      expectMsgPF(3.seconds){
        case r: Receipt ⇒
          r.success should be(false)
          r.message.get.toLowerCase.contains("not found") should be(true)
      }
    }

    "If sendgridService request timeouts, should persist failure to db and return an unsuccessful receipt" in {
      val dao = new MockEmailRequestDao(Some(testRequest))
      val serv = unresponsiveSendgridService
      val courier = priorityCourier(dao, serv)
      courier ! testRequest

      val r = expectMsgType[Receipt](3.seconds)(implicitly[ClassTag[Receipt]])
      r.success should be(false)
      r.errors.exists(_.toLowerCase.contains("timeout"))

      dao.received.length should be(1) //only one save
      dao.received.head.status should be(Some(EmailRequest.Failed))

      serv.underlyingActor.received shouldBe 1
    }

    "If both sendgridService and db timeouts, should return an unsuccessful receipt with both errors" in {
      val dao = new MockEmailRequestDao(Some(testRequest), persistanceTimesOut = true)
      val serv = unresponsiveSendgridService
      val courier = priorityCourier(dao, serv)
      courier ! testRequest

      val r = expectMsgType[Receipt](3.seconds)(implicitly[ClassTag[Receipt]])
      r.success should be(false)
      r.errors.exists(_.toLowerCase.contains("timeout"))
      r.errors.exists(_.toLowerCase.contains("problem"))

      dao.received.length should be(1) //only one save
      dao.received.head.status should be(Some(EmailRequest.Failed))

      serv.underlyingActor.received shouldBe 1
    }






  }


}
