package com.opentok.raven.service.actors

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.pattern._
import akka.util.Timeout
import com.opentok.raven.dal.components.EmailRequestDao
import com.opentok.raven.model.{Email, EmailRequest, Receipt}

/**
 * * Upon receiving a [[com.opentok.raven.model.Requestable]],
 * this actor will attempt to first construct an email, if it's not
 * constructed yet, then it will try to persist an attempt in the db.
 *
 * Regardless of the results of the previous operation but after
 * completed (or timeout), this actor will forward the email to
 * [[com.opentok.raven.service.actors.SendgridActor]], then it will
 * deliver a [[com.opentok.raven.model.Receipt]] with the results
 * back to the requester and finally, as a non-blocking side effect,
 * persist it to the DB.
 *
 * @param emailsDao email requests data access object
 * @param sendridService SendGrid actor instance
 */
class CertifiedCourier(val emailsDao: EmailRequestDao, sendridService: ActorRef, t: Timeout) extends Actor with ActorLogging with Courier {

  import context.dispatcher

  implicit val timeout: Timeout = t

  def send(email: Email, lReq: List[EmailRequest]) = {
    //persist request attempt
    emailRequestDaoActor.ask(lReq).flatMap { i ⇒
      log.info(s"Upserted $i records into db")
      //then pass email to sendgrid service
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
      //todo flatAndThen here to wait for success or failure of inner future
      .andThen(persistSuccessOrFailure(email))
      //install pipe of future receipt to sender
      .pipeTo(sender())
    //template not found, reply and persist attempt
  }

  override def receive: Receive = {
    case em: Email ⇒
      log.info(s"Received email with id ${em.id}")
      //direct email, so we generate a pending email request from every recipient
      send(em, emailToPendingEmailRequests(em))

    case r: EmailRequest ⇒
      log.info(s"Received request with id ${r.id}")

      val req = //at this point, no request should have empty status
        if (r.status.isEmpty) r.copy(status = Some(EmailRequest.Pending))
        else r

      val templateMaybe =
        Email.build(req.id, req.template_id, req.$inject, req.to)

      templateMaybe.map(send(_, req :: Nil)).recover {
        //persist attempt to db,
        case e: Exception ⇒
          emailRequestDaoActor.ask(req.copy(status = Some(EmailRequest.Failed)))
            .andThen(logPersist(req.id))
            .andThen {
              case _ ⇒ //regardless of persist results, send receipt
                sender() ! Receipt.error(e, "unexpected error", requestId = req.id)
            }
      }

    case anyElse ⇒ log.warning(s"Not an acceptable request $anyElse")
  }
}
