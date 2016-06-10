package com.opentok.raven

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.testkit.TestActorRef
import com.opentok.raven.dal.components.EmailRequestDao
import com.opentok.raven.http.JsonProtocol._
import com.opentok.raven.model._
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import spray.json._

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

package object fixture {

  implicit class ToCtx(req: Requestable) {
    def toCtx: RequestContext = RequestContext(req, "trace-1")
  }

  class UnresponsiveActor extends Actor {
    var received = 0

    override def receive: Receive = {
      case _ ⇒ received += 1
    }
  }

  class MockEmailRequestDao(testRequest: Option[EmailRequest],
                            persistanceFails: Boolean = false,
                            persistanceTimesOut: Boolean = false)
                           (implicit system: ActorSystem) extends EmailRequestDao {


    val log = LoggerFactory.getLogger("MockEmailRequestDao")

    lazy val timeout = 5000

    lazy val received = scala.collection.mutable.ListBuffer.empty[EmailRequest]

    def retrieveRequest(id: String)(implicit ctx: ExecutionContext, rctx: RequestContext): Future[Option[EmailRequest]] = {
      log.debug("{}", id)
      if (persistanceFails) Future.failed(new Exception("Could not fetch request"))
      else if (persistanceTimesOut) Future {
        Thread.sleep(timeout)
        testRequest
      }
      else {
        Future(testRequest)
      }
    }

    def persistRequest(req: EmailRequest)(implicit ctx: ExecutionContext, rctx: RequestContext): Future[Int] = {
      if (persistanceFails) Future.failed(new Exception("BOOM"))
      else if (persistanceTimesOut) Future {
        received += req
        Thread.sleep(timeout)
        0
      } else Future {
        received += req
        0
      }
    }
  }

  lazy val testRequest = EmailRequest("ernest+raven@tokbox.com", "test",
    Some(JsObject(Map("a" → JsString(s"INTEGRATION TEST RUN AT ${new DateTime().toString}"),
      "b" → JsNumber(1)))), None, Some("1"))

  lazy val marshalledRequest = requestJsonFormat.write(testRequest)

  lazy val testRequest2 = EmailRequest("ernest+ravenbatchEmail@tokbox.com", "test",
    Some(JsObject(Map("a" → JsString(s"INTEGRATION TEST RUN AT ${new DateTime().toString}"),
      "b" → JsNumber(1)))), None, Some("22222222"))

  lazy val testRequest3 = EmailRequest("ernest+raven@tokbox.com", "test",
    Some(JsObject(Map("a" → JsString(s"UNIT TEST RUN AT ${new DateTime().toString}"),
      "b" → JsNumber(1)))), None, Some("aaaaa"))

  lazy val testEmail =
    Email.build(testRequest2.id, testRequest2.template_id, testRequest2.$inject, testRequest2.to)

  lazy val marshalledEmail = emailJsonFormat.write(testEmail.get)

  class TestActor[T](t: ClassTag[T]) extends Actor with ActorLogging {
    var right = 0
    var wrong = 0

    override def receive: Receive = {
      case _: String ⇒ sender() !(right, wrong)
      case a if t.runtimeClass.isAssignableFrom(a.getClass) ⇒
        right += 1
        sender() ! Receipt.success
        log.info(s"RIGHT MESSAGES RECEIVED $right")
      case _ ⇒
        log.warning(s"WRONG MESSAGES RECEIVED $wrong")
        wrong += 1
        sender() ! Receipt(false, message = Some("Wrong message"))
    }
  }

  def sendgridService(implicit system: ActorSystem): TestActorRef[TestActor[Email]] =
    TestActorRef(Props(classOf[TestActor[Email]], implicitly[ClassTag[Email]]))

  def unresponsiveSendgridService(implicit system: ActorSystem): TestActorRef[UnresponsiveActor] =
    TestActorRef(Props[UnresponsiveActor])

}
