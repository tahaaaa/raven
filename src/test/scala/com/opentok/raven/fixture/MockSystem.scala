package com.opentok.raven.fixture

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.event.LoggingAdapter
import akka.stream.ActorMaterializer
import akka.testkit.TestActorRef
import com.opentok.raven.model.{Email, EmailRequest, Receipt}
import com.opentok.raven.service.{AkkaSystem, Service}

abstract class MockSystem(handler: Props) extends Service with AkkaSystem {

  override implicit val system: ActorSystem = ActorSystem("raven-test")
  override implicit val materializer: ActorMaterializer = ActorMaterializer()
  override val monitoringService, certifiedService, priorityService, mockEmailProvider: ActorRef = TestActorRef(handler)
}

abstract class WorkingMockSystem extends MockSystem(Props(new Actor {
  var received: Any = 0

  implicit val log: LoggingAdapter = context.system.log

  override def receive: Receive = {
    case req: EmailRequest ⇒ sender() ! Receipt.success(None, req.id)
    case em: Email ⇒ sender() ! Receipt.success(None, em.id)
    case msg ⇒ sender() ! Receipt.success
      received = msg
  }
}))
