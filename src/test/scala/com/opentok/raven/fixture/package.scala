package com.opentok.raven

import akka.actor.{ActorSystem, Props, ActorLogging, Actor}
import akka.testkit.TestActorRef
import com.opentok.raven.dal.components.EmailRequestDao
import com.opentok.raven.http.JsonProtocol._
import com.opentok.raven.model.{Email, Receipt, EmailRequest}
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import spray.json._


import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

package object fixture {

  class UnresponsiveActor extends Actor {
    var received = 0

    override def receive: Receive = {
      case _ ⇒ received += 1
    }
  }

  class MockEmailRequestDao(testRequest: Option[EmailRequest],
                            persistanceFails: Boolean = false,
                            persistanceTimesOut: Boolean = false) extends EmailRequestDao {

    val log = LoggerFactory.getLogger("MockEmailRequestDao")

    import scala.concurrent.ExecutionContext.Implicits.global

    lazy val timeout = 20000

    lazy val received = scala.collection.mutable.ListBuffer.empty[EmailRequest]

    def retrieveRequest(id: String)(implicit ctx: ExecutionContext): Future[Option[EmailRequest]] = {
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

    def persistRequest(req: EmailRequest): Future[Int] = {
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
      "b" → JsNumber(1)))), None, None)

  lazy val testRequest3 = EmailRequest("ernest+raven@tokbox.com", "test",
    Some(JsObject(Map("a" → JsString(s"UNIT TEST RUN AT ${new DateTime().toString}"),
      "b" → JsNumber(1)))), None, Some("aaaaa"))

  lazy val testEmail =
    Email.build(testRequest2.id, testRequest2.template_id, testRequest2.$inject, testRequest2.to)

  lazy val nBatch = 3

  lazy val marshalledBatchEmail: JsValue = JsArray(Vector.fill(nBatch)(
    emailJsonFormat.write(testEmail.get.copy(recipients = "BATCH@tokbox.com" :: Nil))).toSeq: _*)

  lazy val marshalledEmail = emailJsonFormat.write(testEmail.get)

  lazy val marshalledBatch: JsValue = JsArray(Vector.fill(nBatch)(requestJsonFormat.write(
    EmailRequest("ernest+ravenbatch@tokbox.com", "test",
      Some(JsObject(Map("a" → JsString(s"INTEGRATION TEST RUN AT ${new DateTime().toString}"),
        "b" → JsNumber(1)))), None, None))).toSeq: _*)

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
