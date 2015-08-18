package com.opentok.raven.service.actors

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.http.scaladsl.model.HttpResponse
import akka.pattern._
import akka.util.Timeout
import com.opentok.raven.GlobalConfig
import com.opentok.raven.dal.components.EmailRequestDao
import com.opentok.raven.model.{EmailRequest, Receipt}

import scala.concurrent.Future

/**
 * Upon receiving an email request, this actor will attempt to forward it
 * to sendgrid straight away and persist the request results after delivering
 * the receipt to the requester.
 *
 * @param emailsDao email requests data access object
 * @param sendgridActor actor instance
 */

class PriorityCourier(emailsDao: EmailRequestDao, sendgridActor: ActorRef) extends Actor with ActorLogging with Courier {

  import context.dispatcher

  implicit val timeout: Timeout = GlobalConfig.ACTOR_TIMEOUT

  override def receive: Receive = {
    case req: EmailRequest ⇒
      log.info(s"Received request with id ${req.id}")
      log.debug("Received {}", req)

      //query sendgrid via sendgridActor and map/recover HttpResponse to Receipt
      sendgridActor.ask(req).mapTo[HttpResponse]
        .map(responseToReceipt(req))
        .recover(exceptionToReceipt(req))
        //install side effecting persist to db, guaranteeing order of callbacks
        .andThen(persistSuccessOrFailure(req, emailsDao))
        //install pipe of future receipt to sender
        .pipeTo(sender())

    case anyElse ⇒ log.warning(s"Not an acceptable request $anyElse")
  }
}
