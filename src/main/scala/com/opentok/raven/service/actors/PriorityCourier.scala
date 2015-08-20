package com.opentok.raven.service.actors

import akka.actor.{Props, Actor, ActorLogging, ActorRef}
import akka.pattern._
import akka.util.Timeout
import com.opentok.raven.dal.components.EmailRequestDao
import com.opentok.raven.model.{EmailRequest, Receipt, Template}
import spray.json.{JsObject, JsValue}

/**
 * Upon receiving an email request, this actor will attempt to forward it
 * to sendgrid straight away and persist the request results after delivering
 * the receipt to the requester.
 *
 * @param emailsDao email requests data access object
 * @param sendgridService actor instance
 */

class PriorityCourier(val emailsDao: EmailRequestDao, sendgridService: ActorRef, t: Timeout)
  extends Actor with ActorLogging with Courier {

  import context.dispatcher

  implicit val timeout: Timeout = t

  override def receive: Receive = {
    case r: EmailRequest ⇒
      log.info(s"Received request with id ${r.id}")
      log.debug("Received {}", r)

      val req = //at this point, no request should have empty status
        if (r.status.isEmpty) r.copy(status = Some(EmailRequest.Pending))
        else r

      val templateMaybe =
        Template.build(req.template_id,
          req.inject.getOrElse(JsObject(Map.empty[String, JsValue])), req.to)

      templateMaybe.map { template ⇒
        //query sendgrid via sendgridActor and map/recover HttpResponse to Receipt
        sendgridService.ask(template).mapTo[Receipt]
          .map(_.copy(requestId = req.id))
          .recover(exceptionToReceipt(req))
          //install side effecting persist to db, guaranteeing order of callbacks
          .andThen(persistSuccessOrFailure(req))
          //install pipe of future receipt to sender
          .pipeTo(sender())
        //template not found, reply and persist attempt
      }.recover(recoverTemplateNotFound(req, sender()))


    case anyElse ⇒ log.warning(s"Not an acceptable request $anyElse")
  }
}
