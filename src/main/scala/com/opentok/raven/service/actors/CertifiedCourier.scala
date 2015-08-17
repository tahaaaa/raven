package com.opentok.raven.service.actors

import akka.actor.{Actor, ActorLogging}
import akka.event.LoggingAdapter
import com.opentok.raven.dal.components.EmailRequestDao
import com.opentok.raven.model.Receipt
import com.opentok.raven.service.actors.EmailSupervisor.RelayEmailCmd

class CertifiedCourier(emails: EmailRequestDao) extends Actor with ActorLogging {

  import context.dispatcher
  implicit val logger: LoggingAdapter = log

  var reqNo = 0

  override def receive: Receive = {
    case req@RelayEmailCmd(msg) ⇒
      reqNo += 1
      log.info(s"Received $req request with id '$reqNo'")
      emails.persistRequest(req).map { i ⇒
        log.info(s"Saved $req with id '$reqNo' successfully into database")
        Receipt.success
      }.recover {
        case e: Exception ⇒
          val msg = "Something went wrong when trying to persist relay request"
          log.error(e, msg)
          Receipt.error(e, msg)
      }
  }
}
