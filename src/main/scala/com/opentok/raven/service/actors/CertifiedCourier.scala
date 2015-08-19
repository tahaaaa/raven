package com.opentok.raven.service.actors

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.http.scaladsl.model.HttpResponse
import akka.pattern._
import com.opentok.raven.GlobalConfig
import com.opentok.raven.dal.components.EmailRequestDao
import com.opentok.raven.model.{Template, EmailRequest, Receipt}
import spray.json.{JsObject, JsValue}

import scala.concurrent.Future

/**
 * Upon receiving an email request, this actor will first
 * persist an attempt in the db, then forward request to sendgridActor
 * to finally update previously saved record to success or failure.
 *
 * @param emailsDao email requests data access object
 * @param sendgridActor actor instance
 */
class CertifiedCourier(emailsDao: EmailRequestDao, sendgridActor: ActorRef) extends Actor with ActorLogging with Courier {

  import GlobalConfig.ACTOR_TIMEOUT
  import context.dispatcher

  override def receive: Receive = {
    case req: EmailRequest ⇒
      log.info(s"Received request with id ${req.id}")
      log.debug("Received {}", req)

      val templateMaybe =
        Template.build(req.template_id,
          req.inject.getOrElse(JsObject(Map.empty[String, JsValue])), req.to)

      templateMaybe.map { template ⇒
        //persist request attempt
        emailsDao.persistRequest(req).flatMap { i ⇒
          sendgridActor.ask(template).mapTo[Receipt]
            .map(_.copy(requestId = req.id))
            .recover(exceptionToReceipt(req))
        }.recoverWith {
          //if persist request attempt fails, we skip step and try to send anyway
          case e: Exception ⇒
            log.warning("There was a problem when trying to save request before forwarding it to sendgridActor. Skipping persist..")
            sendgridActor.ask(template).mapTo[Receipt]
              .map(_.copy(requestId = req.id))
              .recover(exceptionToReceipt(req))
        } //install side effecting persist to db, guaranteeing order of callbacks
          .andThen(persistSuccessOrFailure(req, emailsDao))
          //install pipe of future receipt to sender
          .pipeTo(sender())
        //template not found, reply and persist attempt
      }.recover(recoverTemplateNotFound(req, emailsDao, sender()))

    case anyElse ⇒ log.warning(s"Not an acceptable request $anyElse")
  }
}
