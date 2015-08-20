package com.opentok.raven

import akka.actor.{ActorSystem, Props, ActorLogging, Actor}
import akka.testkit.TestActorRef
import com.opentok.raven.dal.components.EmailRequestDao
import com.opentok.raven.model.{Template, Receipt, EmailRequest}
import org.joda.time.DateTime
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

    import scala.concurrent.ExecutionContext.Implicits.global

    val timeout = 20000

    val received = scala.collection.mutable.ListBuffer.empty[EmailRequest]

    def retrieveRequest(id: String)(implicit ctx: ExecutionContext): Future[Option[EmailRequest]] =
      if (persistanceFails) Future.failed(new Exception("Could not fetch request"))
      else if (persistanceTimesOut) Future {
        Thread.sleep(timeout)
        testRequest
      }
      else Future(testRequest)

    def persistRequest(req: EmailRequest): Future[Int] =
      if (persistanceFails) Future.failed(new Exception("BOOM"))
      else if (persistanceTimesOut) Future {
        received += req
        Thread.sleep(timeout)
        0
      } else Future{
        received += req
        0
      }
  }

  val testRequest = EmailRequest("ernest+raven@tokbox.com", "twirl_test",
    Some(JsObject(Map("a" → JsString(s"INTEGRATION TEST RUN AT ${new DateTime().toString}"),
      "b" → JsNumber(1)))), None, Some("1"))

  val marshalledRequest = EmailRequest.requestJsonFormat.write(testRequest)

  val nBatch = 3

  val marshalledBatch: JsValue = JsArray(Vector.fill(nBatch)(EmailRequest.requestJsonFormat.write(
    EmailRequest("ernest+ravenbatch@tokbox.com", "twirl_test",
      Some(JsObject(Map("a" → JsString(s"INTEGRATION TEST RUN AT ${new DateTime().toString}"),
        "b" → JsNumber(1)))), None, None))).toSeq: _*)

  class TestActor[T](reference: Class[T], t: ClassTag[T]) extends Actor with ActorLogging{
    var right = 0
    var wrong = 0

    override def receive: Receive = {
      case _: String ⇒ sender() ! (right, wrong)
      case a if t.runtimeClass.isAssignableFrom(a.getClass) ⇒
        sender() ! Receipt.success
        right += 1
        log.info(s"RIGHT MESSAGES RECEIVED $right")
      case _ ⇒
        sender() ! Receipt.error(new Exception("Wrong message"), "OOPS")(context.system.log)
        log.error(s"WRONG MESSAGES RECEIVED $wrong")
        wrong += 1
    }
  }

  def sendgridService(implicit system: ActorSystem): TestActorRef[TestActor[Template]] =
    TestActorRef(Props(classOf[TestActor[Template]], classOf[Template],
      implicitly[ClassTag[Template]]))

  def unresponsiveSendgridService(implicit system: ActorSystem): TestActorRef[UnresponsiveActor] =
    TestActorRef(Props[UnresponsiveActor])

}
