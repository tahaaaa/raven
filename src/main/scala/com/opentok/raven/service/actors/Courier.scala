package com.opentok.raven.service.actors

import akka.actor.{Actor, ActorLogging}
import akka.http.scaladsl.model.HttpResponse
import com.opentok.raven.dal.components.EmailRequestDao
import com.opentok.raven.model.{EmailRequest, Receipt}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

/**
 * Common courier methods
 */
trait Courier {
  this: Actor with ActorLogging ⇒

  def persistSuccessOrFailure(req: EmailRequest, emails: EmailRequestDao)(implicit ctx: ExecutionContext): PartialFunction[Try[Receipt], Unit] = {
    case Success(receipt) if receipt.success ⇒
      log.info(s"Successfully forwarded to sendgrid request with id ${req.id}")
      emails.persistRequest(req.copy(status = EmailRequest.Succeeded)).onComplete(logPersist(req))
    case Failure(e) ⇒
      emails.persistRequest(req.copy(status = EmailRequest.Failed)).onComplete(logPersist(req))
      log.error(e, s"There was a problem when forwarding to sendgrid request with id ${req.id}")
  }

  def logPersist(req: EmailRequest): PartialFunction[Try[Int], Unit] = {
    case Success(i) ⇒
      log.info(s"Successfully persisted request with id ${req.id} to database")
    case Failure(e) ⇒
      log.error(e, s"There was an error when persisting request with id ${req.id} to database")
  }

  def exceptionToReceipt(req: EmailRequest):PartialFunction[Throwable, Receipt] = {
    case e: Exception ⇒
      Receipt(success = false, requestId = Some(req.id),
        message = Some("There was a problem when processing email request"),
        errors = e.getCause.getMessage :: e.getMessage :: Nil)
  }

  def responseToReceipt(req: EmailRequest): PartialFunction[HttpResponse, Receipt] = {
    case response if response.status.isSuccess() ⇒
      Receipt(success = true, requestId = Some(req.id))
    case response ⇒ Receipt(success = false, requestId = Some(req.id),
      Some(response.status.defaultMessage()), errors = response.status.reason :: Nil)
  }
}
