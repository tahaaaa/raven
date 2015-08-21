package com.opentok.raven.fixture

import akka.actor.{ActorRef, Props}
import akka.testkit.TestActorRef
import com.opentok.raven.model.Email
import com.opentok.raven.service.AkkaService

import scala.reflect.ClassTag

trait TestAkkaSystem extends AkkaService {
  this: H2Dal with TestConfig with com.opentok.raven.service.System ⇒

  override lazy val smtpService: TestActorRef[TestActor[Email]] =
    TestActorRef(Props(classOf[TestActor[Email]], classOf[Email],
      implicitly[ClassTag[Email]]))

}
