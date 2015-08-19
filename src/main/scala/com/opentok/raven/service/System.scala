package com.opentok.raven.service

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.routing.FromConfig
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.opentok.raven.{RavenConfig, FromResourcesConfig}
import com.opentok.raven.dal.{MysqlDal, Dal}
import com.opentok.raven.service.actors._

trait System {
  implicit val system: ActorSystem
  implicit val materializer: ActorMaterializer

  val smtpService: ActorRef

  val certifiedService: ActorRef
  val priorityService: ActorRef

  val monitoringService: ActorRef
}

trait AkkaSystem extends System {
  this: Dal with RavenConfig ⇒

  implicit val system = ActorSystem("raven")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val smtpService: ActorRef = system.actorOf(Props(classOf[SendgridActor],
    SENDGRID_API_KEY).withRouter(FromConfig), "SMTPService")

  val certifiedService = system.actorOf(
    Props(classOf[EmailSupervisor],
      Props(classOf[CertifiedCourier], emailRequestDao, smtpService, ACTOR_INNER_TIMEOUT),
      CERTIFIED_POOL, emailRequestDao, MAX_RETRIES),
    "certified-service"
  )

  val priorityService = system.actorOf(
    Props(classOf[EmailSupervisor],
      Props(classOf[PriorityCourier], emailRequestDao, smtpService, ACTOR_INNER_TIMEOUT),
      PRIORITY_POOL, emailRequestDao, MAX_RETRIES).withDispatcher("akka.actor.priority-dispatcher"),
    "priority-service")

  val monitoringService = system.actorOf(Props(classOf[MonitoringActor], certifiedService, priorityService, db, driver,
    DB_CHECK, ACTOR_TIMEOUT), "monitoring-service")
}
