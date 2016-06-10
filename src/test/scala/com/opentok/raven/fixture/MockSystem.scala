package com.opentok.raven.fixture

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.event.LoggingAdapter
import akka.stream.ActorMaterializer
import akka.testkit.TestActorRef
import com.opentok.raven.model._
import com.opentok.raven.service.{AkkaSystem, Service}

abstract class MockSystem(handler: Props) extends Service with AkkaSystem {

  override implicit val system: ActorSystem = ActorSystem("raven-test")
  override implicit val materializer: ActorMaterializer = ActorMaterializer()
  override val smtpProvider: Provider = new MockProvider(Receipt.success)
  override val monitoringService, certifiedService, priorityService: ActorRef = TestActorRef(handler)
}

abstract class WorkingMockSystem extends MockSystem(Props(new Actor {

  implicit val log: LoggingAdapter = context.system.log

  override def receive: Receive = {
    case ctx@RequestContext(req: EmailRequest, traceId) ⇒
      sender() ! Receipt.success(None, req.id)
    case ctx@RequestContext(em: Email, traceId) ⇒
      sender() ! Receipt.success(None, em.id)
    //for monitoring endpoints
    case msg ⇒ sender() ! Receipt.success
  }
}))
