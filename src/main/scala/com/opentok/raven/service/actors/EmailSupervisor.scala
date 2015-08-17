package com.opentok.raven.service.actors

import akka.actor.{Props, ActorLogging, Actor, Status}
import akka.event.LoggingAdapter
import com.opentok.raven.dal.components.EmailRequestDao
import akka.pattern.pipe
import com.opentok.raven.model.Receipt
import com.opentok.raven.service.actors.EmailSupervisor.RelayEmailCmd

import scala.util.Success

object EmailSupervisor {

  case class RelayEmailCmd(message: String)

}

class EmailSupervisor(superviseeProps: Props) extends Actor with ActorLogging {

  implicit val logger: LoggingAdapter = log

  import context.dispatcher

  val supervisee = context.actorOf(superviseeProps)

  var reqNo = 0

  override def receive: Receive = {
    case req@RelayEmailCmd(msg) ⇒

  }
}
