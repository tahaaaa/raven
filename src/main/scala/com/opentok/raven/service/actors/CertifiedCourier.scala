package com.opentok.raven.service.actors

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.pattern._
import akka.util.Timeout
import com.opentok.raven.dal.components.EmailRequestDao
import com.opentok.raven.model.{EmailRequest, Receipt, Email}
import spray.json.{JsObject, JsValue}

/**
 * Upon receiving an email request, this actor will first
 * persist an attempt in the db, then forward request to sendgridActor
 * to finally update previously saved record to success or failure.
 *
 * @param emailsDao email requests data access object
 * @param sendridService actor instance
 */
class CertifiedCourier(val emailsDao: EmailRequestDao, sendridService: ActorRef, t: Timeout) extends Actor with ActorLogging with Courier {

  import context.dispatcher

  implicit val timeout: Timeout = t

  def send(email: Email) = {
    //persist request attempt
    emailRequestDaoActor.ask(email: EmailRequest).flatMap { i ⇒
      //then pass template to sendgrid service
      sendridService.ask(email).mapTo[Receipt]
        //map receipt to include id
        .map(_.copy(requestId = email.id))
        //recover exception on sending by mapping exception to unsuccessful receipt
        .recover(exceptionToReceipt(email.id))
    }.recoverWith {
      //we only enter here if emailsDao fails to persist request
      //in which case, we skip persistance step and try to send anyway
      case e: Exception ⇒
        log.warning("There was a problem when trying to save request before forwarding it to sendgridActor. Skipping persist..")
        sendridService.ask(email).mapTo[Receipt].map(_.copy(
          requestId = email.id,
          message = Some("Email delivered but there was a problem when persisting request to db"),
          errors = e.getMessage :: Nil)
        ).recover(exceptionToReceipt(email.id))
    } //install side effecting persist to db, guaranteeing order of callbacks
      .andThen(persistSuccessOrFailure(email: EmailRequest))
      //install pipe of future receipt to sender
      .pipeTo(sender())
    //template not found, reply and persist attempt
  }

  override def receive: Receive = {
    case em: Email ⇒
      log.info(s"Received email with id ${em.id}")
      log.debug("Received {}", em)
      send(em)

    case r: EmailRequest ⇒
      log.info(s"Received request with id ${r.id}")
      log.debug("Received {}", r)

      val req = //at this point, no request should have empty status
        if (r.status.isEmpty) r.copy(status = Some(EmailRequest.Pending))
        else r

      val templateMaybe =
        Email.build(req.id, req.template_id,
          req.inject.getOrElse(JsObject(Map.empty[String, JsValue])), req.to)

      templateMaybe.map(send).recover(recoverTemplateNotFound(req, sender()))

    case anyElse ⇒ log.warning(s"Not an acceptable request $anyElse")
  }
}
