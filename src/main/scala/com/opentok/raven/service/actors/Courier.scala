package com.opentok.raven.service.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern._
import akka.util.Timeout
import com.opentok.raven.dal.components.EmailRequestDao
import com.opentok.raven.model.{Email, EmailRequest, Receipt, Requestable}

import scala.concurrent.{Future, ExecutionContext}
import scala.util.{Failure, Success, Try}

/**
 * Common courier methods
 */
trait Courier {
  this: Actor with ActorLogging ⇒

  import context.dispatcher

  val emailsDao: EmailRequestDao
  implicit val timeout: Timeout

  val emailRequestDaoActor = context.actorOf(Props(classOf[RequestPersister], emailsDao))

  //transforms an email into a list of pending email requests (one per recipient)
  def emailToPendingEmailRequests(em: Email): List[EmailRequest] = em.recipients.map(
    EmailRequest(_, em.fromTemplateId.getOrElse("no_template"),
      None, Some(EmailRequest.Pending), em.id))

  //makes sure that saved requests status is success
  def persistSuccess(req: EmailRequest) = {
    log.info(s"Successfully forwarded to sendgrid, request with id ${req.id}")
    emailRequestDaoActor.ask(req.copy(status = Some(EmailRequest.Succeeded)))
      .onComplete(logPersist(req.id))
  }

  //makes sure that saved requests status is failed
  def persistFailure(req: EmailRequest, anyElse: Any) = {
    emailRequestDaoActor.ask(req.copy(status = Some(EmailRequest.Failed)))
      .onComplete(logPersist(req.id))
    anyElse match {
      case Success(failedReceipt) ⇒
        log.error(s"Receipt from sendgrid Actor success is false $failedReceipt")
      case Failure(e) ⇒
        log.error(e, s"There was a failure in the request with id ${req.id} pipe to sendgridActor")
    }
  }

  //matches given requestable and applies the right transformations
  def persistSuccessOrFailure(req: Requestable): PartialFunction[Try[Receipt], Unit] =
    req match {
      case req: EmailRequest ⇒ {
        case Success(receipt) if receipt.success ⇒ persistSuccess(req)
        case anyElse ⇒ persistFailure(req, anyElse)
      }
      case em: Email ⇒ {
        case Success(receipt) if receipt.success ⇒ emailToPendingEmailRequests(em).foreach(persistSuccess)
        case anyElse ⇒ emailToPendingEmailRequests(em).foreach(persistFailure(_, anyElse))
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
          .andThen(logPersist(req.id))
          .andThen {
          case _ ⇒ //regardless of persist results, send receipt
            requester ! Receipt.error(e, msg, requestId = req.id)(log)
        }
    }

  def logPersist(id: Option[String]): PartialFunction[Try[Any], Unit] = {
    case Success(i) ⇒
      log.info(s"Successfully persisted request with id $id to database")
    case Failure(e) ⇒
      log.error(e, s"There was an error when persisting request with id $id to database")
  }

  def exceptionToReceipt(id: Option[String]): PartialFunction[Throwable, Receipt] = {
    case e: Exception ⇒
      Receipt(success = false, requestId = id,
        message = Some(s"There was a problem when processing email request with id $id"),
        errors = e.getMessage :: Nil)
  }
}

/**
 * Helper actor that wraps emailsDao futures to provide timeout support
 */
class RequestPersister(emailsDao: EmailRequestDao) extends Actor with ActorLogging {

  import context.dispatcher

  val errMsg = "Request persister received unacceptable request"

  val singleMessageRecv: PartialFunction[Any, Future[Int]] = {
    case req: EmailRequest ⇒ emailsDao.persistRequest(req)
    case req ⇒ log.error(errMsg); Future.failed(new Exception(errMsg))
  }

  def receive: Actor.Receive = {
    case lMsg: List[_] ⇒
      val f: Future[Int] = Future.sequence(lMsg.map(singleMessageRecv)).map(_.sum)
      f pipeTo sender()
    case id: String ⇒ emailsDao.retrieveRequest(id) pipeTo sender()
    case msg ⇒ singleMessageRecv(msg) pipeTo sender()
  }
}
