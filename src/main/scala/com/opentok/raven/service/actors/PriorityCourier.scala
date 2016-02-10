package com.opentok.raven.service.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern._
import akka.util.Timeout
import com.opentok.raven.dal.components.EmailRequestDao
import com.opentok.raven.model.{Email, EmailRequest, Receipt}

import scala.concurrent.Future
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
 * @param emailProvider sendgrid actor instance
 */

class PriorityCourier(val emailsDao: EmailRequestDao, val emailProvider: ActorRef, t: Timeout)
  extends Actor with ActorLogging with Courier {

  import context.dispatcher

  implicit val timeout: Timeout = t
  val daoService = context.actorOf(Props(classOf[RequestPersister], emailsDao))

  override def receive: Receive = {

    case r: EmailRequest ⇒
      log.info(s"Received request with id ${r.id}")

      val req = //at this point, no request should have empty status
        if (r.status.isEmpty) r.copy(status = Some(EmailRequest.Pending))
        else r

      val templateMaybe = Email.build(req.id, req.template_id, req.$inject, req.to)

      val receipt: Future[Receipt] = templateMaybe match {
        //successfully built email
        case Success(email) ⇒
          //attempt to send email via emailProvider
          send(req.id, email) andThenPersistResult req
        //error when building email, persist attempt
        case Failure(e) ⇒
          Future.successful(Receipt.error(e, "unexpected error when building template", requestId = req.id))
            //install side effect persist to db
            .andThen {
              case _ ⇒ persistRequest(req.copy(status = Some(EmailRequest.Failed)))
            }
      }
      //pipe future receipt to sender
      receipt pipeTo sender()

    case anyElse ⇒ log.warning(s"Not an acceptable request $anyElse")
  }
}
