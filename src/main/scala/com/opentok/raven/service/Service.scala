package com.opentok.raven.service

import akka.actor.{ActorRef, Props}
import akka.routing.FromConfig
import akka.stream.ActorMaterializer
import com.opentok.raven.RavenConfig
import com.opentok.raven.dal.Dal
import com.opentok.raven.model.{Provider, SendgridProvider}
import com.opentok.raven.service.actors._

/**
 * Trait that declares the actors that make up our service
 */
trait Service {

  implicit val materializer: ActorMaterializer

  val smtpProvider: Provider

  val certifiedService: ActorRef
  val priorityService: ActorRef

  val monitoringService: ActorRef
}

/**
 * This trait creates the actors that make up our application; it can be mixed in with
 * ``Service`` for running code or ``TestKit`` for unit and integration tests.
 */
trait AkkaService extends Service {
  this: System with Dal with RavenConfig ⇒

  implicit val materializer: ActorMaterializer = ActorMaterializer()

  lazy val smtpProvider: Provider = new SendgridProvider(SENDGRID_API_KEY)

  lazy val certifiedService = system.actorOf(
    Props(classOf[EmailSupervisor],
      Props(classOf[CertifiedCourier], emailRequestDao, smtpProvider, ACTOR_TIMEOUT),
      CERTIFIED_POOL, emailRequestDao, MAX_RETRIES, DEFERRER),
    "certified-service"
  )

  lazy val priorityService = system.actorOf(
    Props(classOf[EmailSupervisor],
      Props(classOf[PriorityCourier], emailRequestDao, smtpProvider, ACTOR_TIMEOUT),
      PRIORITY_POOL, emailRequestDao, MAX_RETRIES, DEFERRER).withDispatcher("akka.actor.priority-dispatcher"),
    "priority-service")

  lazy val monitoringService = system.actorOf(Props(classOf[MonitoringActor], certifiedService, priorityService, db, driver,
    DB_CHECK, ACTOR_TIMEOUT), "monitoring-service")
}