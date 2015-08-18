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
 * Upon receiving an email request, this actor will first
 * persist an attempt in the db, then forward request to sendgridActor
 * to finally update previously saved record to success or failure.
 * @param emailsDao email requests data access object
 */
class CertifiedCourier(emailsDao: EmailRequestDao) extends Actor with ActorLogging with Courier {

  import context.dispatcher
  import GlobalConfig.ACTOR_TIMEOUT

  val sendgridActor: ActorRef = context.system.deadLetters

  override def receive: Receive = {
    case req: EmailRequest ⇒
      log.info(s"Received request with id ${req.id}")
      log.debug("Received {}", req)

      //TODO map request to template
      //persist request attempt
      val fReceipt: Future[Receipt] = emailsDao.persistRequest(req).flatMap { i ⇒
        sendgridActor.ask(req).mapTo[HttpResponse]
          .map(responseToReceipt(req))
          .recover(exceptionToReceipt(req))
      }.recoverWith { //if persist request attempt fails, we skip step and try to send anyway
        case e: Exception ⇒
          log.warning("There was a problem when trying to save request before forwarding it to sendgridActor. Skipping persist..")
          sendgridActor.ask(req).mapTo[HttpResponse]
            .map(responseToReceipt(req))
            .recover(exceptionToReceipt(req))
      }

      //install pipe of future receipt to sender
      fReceipt pipeTo sender()

      //install persist success or failure to db
      fReceipt.onComplete(persistSuccessOrFailure(req, emailsDao))

    case anyElse ⇒ log.warning(s"Not an acceptable request $anyElse")
  }
}
