package com.opentok.raven.service

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.pattern.ask
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import akka.util.Timeout
import com.opentok.raven.fixture._
import com.opentok.raven.model.{EmailRequest, Receipt}
import com.opentok.raven.service.actors.MonitoringActor.FailedEmailsCheck
import com.opentok.raven.service.actors.{CertifiedCourier, EmailSupervisor}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.reflect.ClassTag
import scala.util.Random

class EmailSupervisorSpec extends TestKit(ActorSystem("EmailSupervisorSpec"))
with WordSpecLike with Matchers with BeforeAndAfterAll with ImplicitSender {

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  implicit val ctx = system.dispatcher
  implicit val log = system.log

  def newSupervisor(superviseeProps: Props = Props(classOf[TestActor[EmailRequest]], implicitly[ClassTag[EmailRequest]]),
                    mockRequestDao: MockEmailRequestDao = new MockEmailRequestDao(Some(testRequest)),
                    pool: Int = 1,
                    retries: Int = 3,
                    deferrer: Int = 0): TestActorRef[EmailSupervisor] =
    TestActorRef(Props(classOf[EmailSupervisor], superviseeProps, pool, mockRequestDao, retries, deferrer))

  "An EmailSupervisor" should {

    "Load balance request to supervisees correctly" in {
      val rdm = new Random(1000)
      val s = newSupervisor(pool = 2)
      s.underlyingActor.supervisee.length should be(2)
      (0 until 10).foreach(_ ⇒ s ! testRequest.copy(id = Some(rdm.nextInt().toString)))

      val results = Await.result(Future.sequence(s.underlyingActor.supervisee.map(_.ask("gimme")(6.seconds).mapTo[(Int, Int)])), 6.seconds)

      results.reduce { (c, v) ⇒
        (c._1 + v._1, c._2 + v._2)
      } shouldBe(10, 0)
    }

    "try every failed request exactly the maximum number of allowed retries and no more" in {
      val dao = new MockEmailRequestDao(testRequest = Some(testRequest3.copy(status = Some(EmailRequest.Pending))))
      val s = newSupervisor(superviseeProps = Props(new Actor with ActorLogging {
        var received = 0

        def receive: Receive = {
          case req: EmailRequest ⇒ received += 1; log.info("{}", req); sender() ! Receipt(false, requestId = req.id)
          case anyElse ⇒ sender() ! received
        }
      }), retries = 3, pool = 10, mockRequestDao = dao)

      val r = Await.result(s.ask(testRequest3)(10.seconds).mapTo[Receipt], 10.seconds) //1 * 1 + 2 * 1 + 3 * 1 = 6

      r.success should be(false)

      val called = Await.result(Future.sequence(s.underlyingActor.supervisee.map(_.ask("ß")(5.seconds).mapTo[Int])), 5.seconds)

      called.sum should be(3)

    }

    "don't retry if request wasnt previously saved with status pending or failed" in {
      val s = newSupervisor(pool = 10, retries = 5, superviseeProps = Props(classOf[TestActor[Int]], implicitly[ClassTag[Int]]))
      s ! testRequest
      val results = Await.result(Future.sequence(s.underlyingActor.supervisee.map(_.ask("gimme")(3.seconds).mapTo[(Int, Int)])), 4.seconds)
      results.reduce { (c, v) ⇒
        (c._1 + v._1, c._2 + v._2)
      } shouldBe(0, 1)

    }

    "bubble up exceptions from supervisees correctly" in {
      val s = newSupervisor(superviseeProps = Props(classOf[CertifiedCourier],
        new MockEmailRequestDao(Some(testRequest3)), system.deadLetters, 1.seconds: Timeout), retries = 1)
      val r = Await.result(s.ask(testRequest3)(4.second).mapTo[Receipt], 2.seconds)
      r.errors.length > 1 should be(true) //retry error + ask Timeout error
      r.errors.exists(_.toLowerCase.contains("timeout"))
    }
  }

}
