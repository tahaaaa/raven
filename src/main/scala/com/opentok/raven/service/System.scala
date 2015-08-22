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
}

trait AkkaSystem extends System {
  implicit val system = ActorSystem("raven")
}

trait Service {

  implicit val materializer: ActorMaterializer

  val smtpService: ActorRef

  val certifiedService: ActorRef
  val priorityService: ActorRef

  val monitoringService: ActorRef
}

trait AkkaService extends Service {
  this: System with Dal with RavenConfig ⇒

  implicit val materializer: ActorMaterializer = ActorMaterializer()

  lazy val smtpService: ActorRef = system.actorOf(Props(classOf[SendgridActor],
    SENDGRID_API_KEY).withRouter(FromConfig), "SMTPService")

  lazy val certifiedService = system.actorOf(
    Props(classOf[EmailSupervisor],
      Props(classOf[CertifiedCourier], emailRequestDao, smtpService, ACTOR_INNER_TIMEOUT),
      CERTIFIED_POOL, emailRequestDao, MAX_RETRIES, DEFERRER),
    "certified-service"
  )

  lazy val priorityService = system.actorOf(
    Props(classOf[EmailSupervisor],
      Props(classOf[PriorityCourier], emailRequestDao, smtpService, ACTOR_INNER_TIMEOUT),
      PRIORITY_POOL, emailRequestDao, MAX_RETRIES, DEFERRER).withDispatcher("akka.actor.priority-dispatcher"),
    "priority-service")

  lazy val monitoringService = system.actorOf(Props(classOf[MonitoringActor], certifiedService, priorityService, db, driver,
    DB_CHECK, ACTOR_TIMEOUT), "monitoring-service")
}
