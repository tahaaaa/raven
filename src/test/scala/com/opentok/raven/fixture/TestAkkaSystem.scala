package com.opentok.raven.fixture

import akka.actor.{ActorRef, Props}
import com.opentok.raven.model.Template
import com.opentok.raven.service.AkkaService

import scala.reflect.ClassTag

trait TestAkkaSystem extends AkkaService {
  this: H2Dal with TestConfig with com.opentok.raven.service.System ⇒

  override lazy val smtpService: ActorRef =
    system.actorOf(Props(classOf[TestActor[Template]], classOf[Template],
      implicitly[ClassTag[Template]]))

}