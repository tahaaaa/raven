package com.opentok.raven.fixture

import akka.actor.{ActorRef, Props}
import akka.testkit.TestActorRef
import com.opentok.raven.model.{Receipt, Provider, Email}
import com.opentok.raven.service.AkkaService

import scala.concurrent.{Future, ExecutionContext}
import scala.reflect.ClassTag

trait TestAkkaSystem extends AkkaService {
  this: H2Dal with TestConfig with com.opentok.raven.service.System ⇒

  override lazy val smtpProvider: MockProvider = new MockProvider(Receipt.success)
}
