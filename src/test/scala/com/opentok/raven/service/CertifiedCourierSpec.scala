package com.opentok.raven.service

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import akka.util.Timeout
import com.opentok.raven.fixture._
import com.opentok.raven.model.{EmailRequest, Receipt, Template}
import com.opentok.raven.service.actors.CertifiedCourier
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._
import scala.reflect.ClassTag

class CertifiedCourierSpec(_system: ActorSystem) extends TestKit(_system)
with WordSpecLike with Matchers with BeforeAndAfterAll with ImplicitSender {

  def this() = this(ActorSystem("CertifiedCourierSpec"))

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  implicit val ctx = system.dispatcher
  implicit val log = system.log

  def sendgridService(): TestActorRef[TestActor[Template]] =
    TestActorRef(Props(classOf[TestActor[Template]], classOf[Template],
      implicitly[ClassTag[Template]]))

  def unresponsiveSendgridService(): TestActorRef[UnresponsiveActor] =
    TestActorRef(Props[UnresponsiveActor])

  def certifiedCourier(mockRequestDao: MockEmailRequestDao,
                       serv: TestActorRef[_]) =
    TestActorRef(Props(classOf[CertifiedCourier],
      mockRequestDao, serv, 1.seconds: Timeout))

  "A Certified Courier" must {

    "First attempt to persist request to db and then pass it to sendgridService" in {
      val dao = new MockEmailRequestDao(Some(testRequest))
      val serv = sendgridService()
      val courier = certifiedCourier(dao, serv)
      courier ! testRequest

      expectMsg[Receipt](Receipt.success(None, testRequest.id))

      serv.underlyingActor.right shouldBe 1
      serv.underlyingActor.wrong shouldBe 0

      dao.received.head.status shouldBe Some(EmailRequest.Pending) //first save with status pending
      dao.received.tail.head.status shouldBe Some(EmailRequest.Succeeded) //then save success
    }

    "If first attempt to persist fails, try to send anyway but change success receipt message with warning" in {
      val dao = new MockEmailRequestDao(Some(testRequest), persistanceFails = true)
      val serv = sendgridService()
      val courier = certifiedCourier(dao, serv)
      courier ! testRequest

      val r = expectMsgType[Receipt](2.seconds)(implicitly[ClassTag[Receipt]])
      r.success should be(true)
      r.message.get.contains("problem") should be(true)

      serv.underlyingActor.right shouldBe 1
      serv.underlyingActor.wrong shouldBe 0
    }

    "If attempt to persist timeouts, try to send anyway but change success receipt message with warning" in {
      val dao = new MockEmailRequestDao(Some(testRequest), persistanceTimesOut = true)
      val serv = sendgridService()
      val courier = certifiedCourier(dao, serv)
      courier ! testRequest

      val r = expectMsgType[Receipt](3.seconds)(implicitly[ClassTag[Receipt]])
      r.success should be(true)
      r.message.get.contains("problem") should be(true)

      serv.underlyingActor.right shouldBe 1
      serv.underlyingActor.wrong shouldBe 0
    }

    "If sendgridService request timeouts, should persist failure to db and return an unsuccessful receipt" in {
      val dao = new MockEmailRequestDao(Some(testRequest))
      val serv = unresponsiveSendgridService()
      val courier = certifiedCourier(dao, serv)
      courier ! testRequest

      val r = expectMsgType[Receipt](3.seconds)(implicitly[ClassTag[Receipt]])
      r.success should be(false)
      r.errors.exists(_.toLowerCase.contains("timeout"))

      dao.received.length should be(2) //first and second try
      dao.received.head.status should be(Some(EmailRequest.Pending)) //first save with pending
      dao.received.tail.head.status should be(Some(EmailRequest.Failed)) //then save failed

      serv.underlyingActor.received shouldBe 1
    }

    "If both sendgridService and db timeouts, should return an unsuccessful receipt with both errors" in {
      val dao = new MockEmailRequestDao(Some(testRequest), persistanceTimesOut = true)
      val serv = unresponsiveSendgridService()
      val courier = certifiedCourier(dao, serv)
      courier ! testRequest

      val r = expectMsgType[Receipt](3.seconds)(implicitly[ClassTag[Receipt]])
      r.success should be(false)
      r.errors.exists(_.toLowerCase.contains("timeout"))
      r.errors.exists(_.toLowerCase.contains("problem"))

      dao.received.length should be(2) //first and second try
      dao.received.head.status should be(Some(EmailRequest.Pending)) //first save with pending
      dao.received.tail.head.status should be(Some(EmailRequest.Failed)) //then save failed

      serv.underlyingActor.received shouldBe 1
    }

  }

}
