package com.opentok.raven.service

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.stream.ActorMaterializer
import com.opentok.raven.GlobalConfig
import com.opentok.raven.dal.{MysqlDal, Dal}
import com.opentok.raven.service.actors._

trait System {
  implicit val system: ActorSystem
  implicit val materializer: ActorMaterializer

  val certifiedService: ActorRef
  val priorityService: ActorRef

  val monitoringService: ActorRef
}

trait AkkaSystem extends System {
  this: Dal ⇒

  implicit val system = ActorSystem("raven")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val sendgridActor: ActorRef = system.actorOf(Props[SendgridActor], "SendgridActor")

  val certifiedService = system.actorOf(
    Props(classOf[EmailSupervisor],
      Props(classOf[CertifiedCourier], emailRequestDao, sendgridActor),
      GlobalConfig.CERTIFIED_POOL, emailRequestDao),
    "certified-service"
  )

  val priorityService = system.actorOf(
    Props(classOf[EmailSupervisor],
      Props(classOf[PriorityCourier], emailRequestDao, sendgridActor),
      GlobalConfig.PRIORITY_POOL, emailRequestDao).withDispatcher("akka.actor.priority-dispatcher"),
    "priority-service")

  val monitoringService = system.actorOf(Props(classOf[MonitoringActor], certifiedService, priorityService, db, driver), "monitoring-service")
}
