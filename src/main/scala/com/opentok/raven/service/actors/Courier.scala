package com.opentok.raven.service.actors

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.http.scaladsl.model.HttpResponse
import com.opentok.raven.dal.components.EmailRequestDao
import com.opentok.raven.model.{EmailRequest, Receipt}
import akka.pattern._

import scala.concurrent.{Future, ExecutionContext}
import scala.util.{Failure, Success, Try}

/**
 * Common courier methods
 */
trait Courier {
  this: Actor with ActorLogging ⇒


  def persistSuccessOrFailure(req: EmailRequest, dao: EmailRequestDao)
                             (implicit ctx: ExecutionContext): PartialFunction[Try[Receipt], Unit] = {
    case Success(receipt) if receipt.success ⇒
      log.info(s"Successfully forwarded to sendgrid, request with id ${req.id}")
      dao.persistRequest(req.copy(status = Some(EmailRequest.Succeeded))).onComplete(logPersist(req))
    case anyElse ⇒
      dao.persistRequest(req.copy(status = Some(EmailRequest.Failed))).onComplete(logPersist(req))
      anyElse match {
        case Success(failedReceipt) ⇒
          log.error(s"Receipt from sendgrid Actor success is false $failedReceipt")
        case Failure(e) ⇒
          log.error(e, s"There was a failure in the request with id ${req.id} pipe to sendgridActor")
      }
  }

  def recoverTemplateNotFound(req: EmailRequest, dao: EmailRequestDao, requester: ActorRef)
                             (implicit ctx: ExecutionContext) =
    PartialFunction.apply[Throwable, (String, Throwable)] {
      case e: ClassCastException ⇒ (s"One of the injection variables for '${req.template_id}' does not have the right type", e)
      case e: NoSuchElementException ⇒ (s"Request is missing injection variables for template with id '${req.template_id}'", e)
      case e: MatchError ⇒
        (s"Template with id '${req.template_id}' not found", e)
      case e: Exception ⇒
        (s"There was a problem when building html template", e)
    }.andThen {
      //persist attempt to db,
      case (msg, e) ⇒
        dao.persistRequest(req)
          .andThen(logPersist(req))
          .andThen {
          case _ ⇒ //regardless of persist results, send receipt
            requester ! Receipt.error(e, msg, requestId = req.id)(log)
        }
    }

  def logPersist(req: EmailRequest): PartialFunction[Try[Int], Unit] = {
    case Success(i) ⇒
      log.info(s"Successfully persisted request with id ${req.id} to database")
    case Failure(e) ⇒
      log.error(e, s"There was an error when persisting request with id ${req.id} to database")
  }

  def exceptionToReceipt(req: EmailRequest): PartialFunction[Throwable, Receipt] = {
    case e: Exception ⇒
      Receipt(success = false, requestId = req.id,
        message = Some(s"There was a problem when processing email request with id ${req.id}"),
        errors = e.getMessage :: Nil)
  }
}
