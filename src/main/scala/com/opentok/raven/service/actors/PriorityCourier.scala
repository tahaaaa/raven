package com.opentok.raven.service.actors

import akka.actor.{Props, Actor, ActorLogging, ActorRef}
import akka.pattern._
import akka.util.Timeout
import com.opentok.raven.dal.components.EmailRequestDao
import com.opentok.raven.model.{EmailRequest, Receipt, Email}
import spray.json.{JsObject, JsValue}

/**
 * Upon receiving an email request, this actor will attempt to forward it
 * to sendgrid straight away and deliver the receipt to the requester after
 * persisting the request results.
 *
 * @param emailsDao email requests data access object
 * @param sendgridService actor instance
 */

class PriorityCourier(val emailsDao: EmailRequestDao, sendgridService: ActorRef, t: Timeout)
  extends Actor with ActorLogging with Courier {

  import context.dispatcher

  implicit val timeout: Timeout = t

  def send(em: Email, id: Option[String]) = {
    val sdr = sender()
    log.info("Forwarding email to sendgrid actor with timeout {}", timeout)
    //query sendgrid via sendgridActor and map/recover HttpResponse to Receipt
    sendgridService.ask(em).mapTo[Receipt]
      .map(_.copy(requestId = id))
      .recover(exceptionToReceipt(id))
      //install side effecting persist to db, guaranteeing order of callbacks
      .andThen(persistSuccessOrFailure(em))
      //install pipe of future receipt to sender
      .pipeTo(sdr)
  }

  override def receive: Receive = {
    case e: Email ⇒ //not used at the moment
      log.info(s"Received email with id ${e.id}")
      send(e, e.id)

    case r: EmailRequest ⇒
      log.info(s"Received request with id ${r.id}")

      val req = //at this point, no request should have empty status
        if (r.status.isEmpty) r.copy(status = Some(EmailRequest.Pending))
        else r

      val templateMaybe =
        Email.build(req.id, req.template_id,
          req.inject.getOrElse(JsObject(Map.empty[String, JsValue])), req.to :: Nil)

      templateMaybe.map(send(_, req.id))
        //template not found, reply and persist attempt
        .recover(recoverTemplateNotFound(req, sender()))


    case anyElse ⇒ log.warning(s"Not an acceptable request $anyElse")
  }
}
