package com.opentok.raven.service.actors

import akka.actor.{Actor, ActorLogging}
import akka.event.LoggingAdapter
import akka.pattern._


object SendgridActor {
  //TODO
}

class SendgridActor extends Actor with ActorLogging {

  import context.dispatcher

  implicit val logger: LoggingAdapter = log
  //TODO 


  override def receive: Receive = {
    case _ ⇒ sender() ! "OK"
//    case _ ⇒ (context.system.deadLetters ? "") pipeTo sender()
  }
}
