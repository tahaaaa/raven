package com.opentok.raven.service

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.pattern.ask
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import akka.util.Timeout
import com.opentok.raven.fixture._
import com.opentok.raven.model.{EmailRequest, Receipt}
import com.opentok.raven.service.actors.MonitoringActor.PendingEmailsCheck
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
                    deferrer: Int = 1): TestActorRef[EmailSupervisor] =
    TestActorRef(Props(classOf[EmailSupervisor], superviseeProps, pool, mockRequestDao, retries, deferrer))

  "An EmailSupervisor" must {

    "Load balance request to supervisees correctly" in {
      val rdm = new Random(1000)
      val s = newSupervisor(pool = 100)
      s.underlyingActor.supervisee.length should be(100)
      (0 until 10000).foreach(_ ⇒ s ! testRequest.copy(id = Some(rdm.nextInt().toString)))

      val results = Await.result(Future.sequence(s.underlyingActor.supervisee.map(_.ask("gimme")(3.seconds).mapTo[(Int, Int)])), 4.seconds)

      results.map { ab ⇒
        ab._1 + ab._2 should not be (0)
        ab
      }.reduce { (c, v) ⇒
        (c._1 + v._1, c._2 + v._2)
      } shouldBe(10000, 0)
    }

    "Load balance list of requests to supervisees correctly" in {
      val rdm = new Random(1000)
      val s = newSupervisor(pool = 100)
      s.underlyingActor.supervisee.length should be(100)
      (0 until 100).foreach(_ ⇒ s ! Vector.fill(10)(testRequest.copy(id = Some(rdm.nextInt().toString))))

      val results = Await.result(Future.sequence(s.underlyingActor.supervisee.map(_.ask("gimme")(3.seconds).mapTo[(Int, Int)])), 4.seconds)
      results.map { ab ⇒
        ab._1 + ab._2 should not be (0)
        ab
      }.reduce { (c, v) ⇒
        (c._1 + v._1, c._2 + v._2)
      } shouldBe(1000, 0)
    }

    "report with pending requests" in {
      //this is going to reply with receipt false so we should have time to chech on pending emails
      val s = newSupervisor(superviseeProps = Props(classOf[TestActor[Int]], implicitly[ClassTag[Int]]), retries = 50)
      val pre = Await.result(s.ask(PendingEmailsCheck)(2.second), 2.seconds)
      pre.isInstanceOf[Map[_, _]] should be(true)
      pre.asInstanceOf[Map[String, Int]].isEmpty should be(true)

      s ! testRequest
      s ! testRequest3

      s.underlyingActor.pending.isEmpty should be(false)
      s.underlyingActor.pending.exists(_._1.request.id == testRequest3.id) should be(true)
      s.underlyingActor.pending.exists(_._1.request.id == testRequest.id) should be(true)

      val after = Await.result(s.ask(PendingEmailsCheck)(2.second).mapTo[Map[String, Int]], 2.seconds)
      after.isEmpty should be(false)
      after.exists(_._1 == testRequest.id.get) should be(true)
      after.exists(_._1 == testRequest3.id.get) should be(true)
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

      val r = Await.result(s.ask(testRequest3)(7.seconds).mapTo[Receipt], 7.seconds) //1 * 1 + 2 * 1 + 3 * 1 = 6

      r.success should be(false)

      val called = Await.result(Future.sequence(s.underlyingActor.supervisee.map(_.ask("ß")(2.seconds).mapTo[Int])), 2.seconds)

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
      val r = Await.result(s.ask(testRequest3)(1.second).mapTo[Receipt], 2.seconds)
      r.errors.length > 1 should be(true) //retry error + ask Timeout error
      r.errors.exists(_.toLowerCase.contains("timeout"))
    }
  }

}
