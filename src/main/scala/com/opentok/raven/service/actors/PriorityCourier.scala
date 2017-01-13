package com.opentok.raven.service.actors

import akka.actor.{Actor, Props}
import akka.pattern._
import akka.util.Timeout
import build.unstable.tylog.Variation
import com.opentok.raven.dal.components.EmailRequestDao
import com.opentok.raven.model._
import org.slf4j.event.Level

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
 * Upon receiving a [[com.opentok.raven.model.Requestable]],
 * this actor will attempt to first construct an email, if it's not
 * constructed yet, then it will pass email to
 * [[com.opentok.raven.model.SendgridProvider]], then it will
 * deliver a [[com.opentok.raven.model.Receipt]] with the results
 * back to the requester and finally, as a non-blocking side effect,
 * persist it to the DB.
 *
 * @param emailsDao email requests data access object
 * @param provider SMTP provider
 */

class PriorityCourier(val emailsDao: EmailRequestDao, val provider: Provider, val timeout: Timeout)
  extends Actor with Courier {

  import context.dispatcher

  val daoService = context.actorOf(Props(classOf[RequestPersister], emailsDao))

  override def receive: Receive = {

    case ctx@RequestContext(r: EmailRequest, traceId) ⇒

      implicit val rctx: RequestContext = ctx

      val req = //at this point, no request should have empty status
        if (r.status.isEmpty) r.copy(status = Some(EmailRequest.Pending))
        else r

      log.tylog(Level.INFO, traceId, BuildEmail, Variation.Attempt, self.path.toString)

      val templateMaybe = Email.build(req.id, req.template_id, req.$inject, req.to)

      val receipt: Future[Receipt] = templateMaybe match {

        //successfully built email
        case Success(email) ⇒
          log.tylog(Level.INFO, traceId, BuildEmail, Variation.Success, self.path.toString)
          //attempt to send email via emailProvider
          send(req.id, email) andThenPersistResult req

        //error when building email, persist attempt
        case Failure(e) ⇒
          val msg = s"unexpected error when building template ${req.template_id}"
          log.tylog(Level.INFO, traceId, BuildEmail, Variation.Failure(e), msg)
          Future.successful(Receipt.error(e, msg, requestId = req.id))
            //install side effect persist to db
            .andThen {
              case _ ⇒ persistRequest(req.copy(status = Some(EmailRequest.Failed)))
            }
      }
      //pipe future receipt to sender
      receipt pipeTo sender()

    case anyElse ⇒ log.warning("Not an acceptable request {}", anyElse)
  }
}
