package com.opentok.raven.fixture

import akka.actor.{Props, ActorRef, Actor, ActorSystem}
import akka.stream.ActorMaterializer
import akka.testkit.TestActorRef
import com.opentok.raven.model.Receipt
import com.opentok.raven.service.{AkkaSystem, Service}
import com.opentok.raven.service.actors.EmailSupervisor

abstract class MockSystem[T](handler: Props) extends Service with AkkaSystem {

  override implicit val system: ActorSystem = ActorSystem("raven-test")
  override implicit val materializer: ActorMaterializer = ActorMaterializer()
  override val monitoringService, certifiedService, priorityService, smtpService: ActorRef = TestActorRef(handler)
}

abstract class WorkingMockSystem extends MockSystem(Props(new Actor {
  var received: Any = 0

  override def receive: Receive = {
    case msg ⇒ sender() ! Receipt.success
      received = msg
  }
}))
