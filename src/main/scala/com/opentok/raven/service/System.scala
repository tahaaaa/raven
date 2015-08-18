package com.opentok.raven.service

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.stream.ActorMaterializer
import com.opentok.raven.dal.{MysqlDal, Dal}
import com.opentok.raven.service.actors.{CertifiedCourier, MonitoringActor, PriorityCourier, EmailSupervisor}

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

  val certifiedService = system.actorOf(Props(classOf[EmailSupervisor],
    Props(classOf[CertifiedCourier], emailRequestDao)), "certified-service")

  val priorityService = system.actorOf(Props(classOf[EmailSupervisor],
    Props(classOf[PriorityCourier], emailRequestDao)).withDispatcher("akka.actor.priority-dispatcher"), "priority-service")

  val monitoringService = system.actorOf(Props(classOf[MonitoringActor], certifiedService, priorityService, db, driver, materializer), "monitoring-service")
}
