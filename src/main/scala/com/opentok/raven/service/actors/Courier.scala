package com.opentok.raven.service.actors

import akka.actor.{Props, Actor, ActorLogging, ActorRef}
import akka.util.Timeout
import com.opentok.raven.dal.components.EmailRequestDao
import com.opentok.raven.model.{EmailRequest, Receipt}
import akka.pattern._

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

/**
 * Common courier methods
 */
trait Courier {
  this: Actor with ActorLogging ⇒
  
  val emailsDao: EmailRequestDao
  implicit val timeout: Timeout

  val emailRequestDaoActor = context.actorOf(Props(classOf[RequestPersister], emailsDao))

  def persistSuccessOrFailure(req: EmailRequest)
                             (implicit ctx: ExecutionContext): PartialFunction[Try[Receipt], Unit] = {
    case Success(receipt) if receipt.success ⇒
      log.info(s"Successfully forwarded to sendgrid, request with id ${req.id}")
      emailRequestDaoActor.ask(req.copy(status = Some(EmailRequest.Succeeded)))
        .mapTo[Int].onComplete(logPersist(req))
    case anyElse ⇒
      emailRequestDaoActor.ask(req.copy(status = Some(EmailRequest.Failed)))
        .mapTo[Int].onComplete(logPersist(req))
      anyElse match {
        case Success(failedReceipt) ⇒
          log.error(s"Receipt from sendgrid Actor success is false $failedReceipt")
        case Failure(e) ⇒
          log.error(e, s"There was a failure in the request with id ${req.id} pipe to sendgridActor")
      }
  }

  def recoverTemplateNotFound(req: EmailRequest, requester: ActorRef)
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
        emailRequestDaoActor.ask(req)
          .mapTo[Int]
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

/**
 * Helper actor that wraps emailsDao futures to provide timeout support
 */
class RequestPersister(emailsDao: EmailRequestDao) extends Actor {
  import context.dispatcher
  def receive: Actor.Receive = {
    case req: EmailRequest ⇒ emailsDao.persistRequest(req) pipeTo sender()
    case id: String ⇒ emailsDao.retrieveRequest(id) pipeTo sender()
  }
}
