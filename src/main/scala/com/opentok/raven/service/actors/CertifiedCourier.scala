package com.opentok.raven.service.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern._
import akka.util.Timeout
import com.opentok.raven.dal.components.EmailRequestDao
import com.opentok.raven.model.{Email, EmailRequest, Receipt}

import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

/**
 * * Upon receiving a [[com.opentok.raven.model.Requestable]],
 * this actor will attempt to first construct an email, if it's not
 * constructed yet, then it will try to persist an attempt in the db.
 *
 * Regardless of the results of the previous operation but after
 * completed (or timeout), this actor will forward the email to
 * [[com.opentok.raven.service.actors.SendgridActor]], then
 * as a non-blocking side effect, persist it to the DB and finally,
 * it willdeliver a [[com.opentok.raven.model.Receipt]] with the results
 * back to the requester.
 *
 * @param emailsDao email requests data access object
 * @param emailProvider SendGrid actor instance
 */
class CertifiedCourier(val emailsDao: EmailRequestDao, val emailProvider: ActorRef, t: Timeout) extends Actor with ActorLogging with Courier {

  import context.dispatcher

  implicit val timeout: Timeout = t
  val daoService = context.actorOf(Props(classOf[RequestPersister], emailsDao))

  /**
   * transforms an email into a list of pending email requests (one per recipient)
   */
  def emailToPendingEmailRequests(em: Email): List[EmailRequest] = em.recipients.map(
    EmailRequest(_, em.fromTemplateId.getOrElse("no_template"),
      None, Some(EmailRequest.Pending), em.id))

  def sendEmail(reqs: List[EmailRequest], email: Email): Future[Receipt] = {
    //persist request attempt
    persistRequests(reqs).flatMap { _ ⇒
      //then send email to email provider
      send(reqs.head.id, email)
    }.recoverWith {
      //we only enter this block if emailsDao fails to persist request
      //because send recovers itself with an error receipt
      //in which case, we skip persistance step and try to send anyway
      case e: Exception ⇒
        log.warning("There was a problem when trying to save request BEFORE forwarding it to provider. Skipping persist..")
        //add error in errors but leave receipt success as it is
        send(reqs.head.id, email).map(_.copy(
          message = Some("email delivered but there was a problem when persisting request to db"),
          errors = e.getMessage :: Nil)
        )
    }.recover {
      //we only enter this block if the send after recovering the failed persist fails (recoverWith future fails)
      case e: Exception ⇒ Receipt.error(e, "failed to recover failed persist attempt into send: send failed", None)
    } andThenPersistResult reqs
  }

  override def receive: Receive = {
    case em: Email ⇒
      log.info(s"Received email with id ${em.id}")
      //direct email, so we generate a pending email request for every recipient
      sendEmail(emailToPendingEmailRequests(em), em) pipeTo sender()

    case r: EmailRequest ⇒
      log.info(s"Received request with id ${r.id}")

      val req = //at this point, no request should have empty status
        if (r.status.isEmpty) r.copy(status = Some(EmailRequest.Pending))
        else r

      val templateMaybe =
        Email.build(req.id, req.template_id, req.$inject, req.to)

      val receipt: Future[Receipt] = templateMaybe match {
        //successfully built template
        case Success(email) ⇒ sendEmail(req :: Nil, email)
        //persist failed attempt to db,
        case Failure(e) ⇒
          val msg = "unexpected error when building template"
          log.error(e, msg)
          val p = Promise[Receipt]()
          persistRequest(req.copy(status = Some(EmailRequest.Failed)))
            .onComplete { _ ⇒
              //regardless of persist results, send receipt
              p complete Success(Receipt.error(e, msg, requestId = req.id))
            }
          p.future
      }

      receipt pipeTo sender()

    case anyElse ⇒ log.warning(s"Not an acceptable request $anyElse")
  }

}
