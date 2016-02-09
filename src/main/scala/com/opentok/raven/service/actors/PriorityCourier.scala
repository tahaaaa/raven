package com.opentok.raven.service.actors

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.pattern._
import akka.util.Timeout
import com.opentok.raven.dal.components.EmailRequestDao
import com.opentok.raven.model.{Email, EmailRequest, Receipt}

import scala.util.{Failure, Success}

/**
 * Upon receiving a [[com.opentok.raven.model.Requestable]],
 * this actor will attempt to first construct an email, if it's not
 * constructed yet, then it will try to forward it to
 * [[com.opentok.raven.service.actors.SendgridActor]], then it will
 * deliver a [[com.opentok.raven.model.Receipt]] with the results
 * back to the requester and finally, as a non-blocking side effect,
 * persist it to the DB.
 *
 * @param emailsDao email requests data access object
 * @param sendgridService sendgrid actor instance
 */

class PriorityCourier(val emailsDao: EmailRequestDao, sendgridService: ActorRef, t: Timeout)
  extends Actor with ActorLogging with Courier {

  import context.dispatcher

  implicit val timeout: Timeout = t

  def send(em: Email, id: Option[String]) = {
    val sdr = sender()
    log.info("Forwarding email to sendgrid actor with timeout {}", timeout)
    //query sendgrid via sendgridActor and map/recover HttpResponse to Receipt
    val receipt = sendgridService.ask(em).mapTo[Receipt]
      .map(_.copy(requestId = id))
      .recover(exceptionToReceipt(id))

    //pipe future receipt to sender
    receipt pipeTo sdr

    //install side effecting persist to db, guaranteeing order of callbacks
    receipt.andThen {
      case Success(r) if r.success ⇒ emailToPendingEmailRequests(em).foreach(persistSuccess)
      case tr@Success(r) ⇒ emailToPendingEmailRequests(em).foreach(persistFailure(_, tr))
      case tr@Failure(error) ⇒ emailToPendingEmailRequests(em).foreach(persistFailure(_, tr))
    }
  }

  override def receive: Receive = {

    case r: EmailRequest ⇒
      log.info(s"Received request with id ${r.id}")

      val req = //at this point, no request should have empty status
        if (r.status.isEmpty) r.copy(status = Some(EmailRequest.Pending))
        else r

      val templateMaybe =
        Email.build(req.id, req.template_id, req.$inject, req.to)

      templateMaybe.map(send(_, req.id))
        //template not found, reply and persist attempt
        .recover {
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
