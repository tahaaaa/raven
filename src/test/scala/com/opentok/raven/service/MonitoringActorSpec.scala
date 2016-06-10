package com.opentok.raven.service

import akka.actor.{Identify, Props, ActorSystem}
import akka.testkit.{TestActorRef, TestKit, ImplicitSender}
import akka.util.Timeout
import com.opentok.raven.fixture.{TestConfig, H2Dal}
import com.opentok.raven.model.Receipt
import com.opentok.raven.service.actors.MonitoringActor
import com.opentok.raven.service.actors.MonitoringActor.ComponentHealthCheck
import org.scalatest.{WordSpecLike, Matchers, BeforeAndAfterAll}

import scala.concurrent.Future
import scala.concurrent.duration._

class MonitoringActorSpec() extends TestKit(ActorSystem("MonitoringActorSpec"))
with WordSpecLike with Matchers with BeforeAndAfterAll with ImplicitSender with TestConfig {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  implicit val ctx = system.dispatcher
  implicit val log = system.log
  val dal = new H2Dal {}

  def newActor: TestActorRef[MonitoringActor] =
    TestActorRef(Props(classOf[MonitoringActor], self, self,
      () ⇒ dal.testDalConnectivity(), ACTOR_TIMEOUT))

  "Monitoring Actor" should {

    "be subscribed at requests that fail" in {
      val m = newActor

      assert(m.underlyingActor.failed.isEmpty)

      system.eventStream.publish(Receipt.success)

      assert(m.underlyingActor.failed.isEmpty)

      system.eventStream.publish(Receipt.error(new Exception("BOOM"), "bam"))

      assert(m.underlyingActor.failed.length == 1)
    }

    "limit number of failed requests stored" in {
      val m = newActor

      assert(m.underlyingActor.failed.isEmpty)

      (0 until m.underlyingActor.MAX_FAILED + 10).foreach { _ ⇒
        system.eventStream.publish(Receipt.error(new Exception("BOOM"), "bam"))
      }

      assert(m.underlyingActor.failed.length == m.underlyingActor.MAX_FAILED)
    }

    "report number of failed requests" in {
      val m = newActor
      val err = Receipt.error(new Exception("BOOM"), "bam")
      system.eventStream.publish(err)

      m ! MonitoringActor.FailedEmailsCheck

      expectMsg(Vector(err))

    }

    "check service's health" in {
      //everything in order
      val m = newActor
      m ! ComponentHealthCheck("service")
      val r = expectMsgType[Receipt]
      assert(r.success)

      //problem
      val prob: TestActorRef[MonitoringActor] =
        TestActorRef(Props(classOf[MonitoringActor], self, system.deadLetters,
          () ⇒ dal.testDalConnectivity(), Timeout(10.millis)))

      prob ! ComponentHealthCheck("service")

      val r2 = expectMsgType[Receipt]
      assert(!r2.success)
    }

    val failingDal = new H2Dal {
      override def testDalConnectivity(): Future[Int] = Future.failed(new Exception("bOOM"))
    }

    "check dal's health" in {
      //everything in order
      val m = newActor
      m ! ComponentHealthCheck("dal")
      val r = expectMsgType[Receipt]
      assert(r.success)

      //problem
      val prob: TestActorRef[MonitoringActor] =
        TestActorRef(Props(classOf[MonitoringActor], self, system.deadLetters,
          () ⇒ failingDal.testDalConnectivity(), Timeout(10.millis)))

      prob ! ComponentHealthCheck("dal")

      val r2 = expectMsgType[Receipt]
      assert(!r2.success)
    }
  }
}
