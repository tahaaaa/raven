package com.opentok.raven.service.actors

import akka.actor.{Actor, ActorLogging, Props}
import akka.pattern._
import akka.util.Timeout
import com.opentok.raven.dal.components.EmailRequestDao
import com.opentok.raven.model.{Email, EmailRequest, Receipt, Requestable}

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/**
 * Common methods used by [[com.opentok.raven.service.actors.PriorityCourier]] and
 * [[com.opentok.raven.service.actors.CertifiedCourier]]
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
  def persistSuccess(req: EmailRequest): Future[_] = {
    log.info(s"Changing request with id ${req.id} status to Success")
    emailRequestDaoActor.ask(req.copy(status = Some(EmailRequest.Succeeded)))
      .andThen(logPersist(req.id))
  }

  //makes sure that saved requests status is failed
  def persistFailure(req: EmailRequest, receipt: Try[Receipt]): Future[_] = {
    log.info(s"Changing request with id ${req.id} status to Failed")
    val f = emailRequestDaoActor.ask(req.copy(status = Some(EmailRequest.Failed)))
      .andThen(logPersist(req.id))
    receipt match {
      case Success(failedReceipt) ⇒
        log.warning(s"Receipt from sendgrid Actor success is false $failedReceipt")
      case Failure(e) ⇒
        log.error(e, s"There was a failure in the request with id ${req.id} pipe to sendgridActor")
    }
    f
  }

  def logPersist(id: Option[String]): PartialFunction[Try[Any], Unit] = {
    case Success(i) ⇒
      log.info(s"Successfully persisted request with id $id to database")
    case Failure(e) ⇒
      log.error(e, s"There was an error when persisting request with id $id to database")
  }

  def exceptionToReceipt(id: Option[String]): PartialFunction[Throwable, Receipt] = {
    case e: Exception ⇒
      val msg = s"There was a problem when processing email request with id $id"
      log.error(e, msg)
      Receipt(success = false, requestId = id,
        message = Some(msg),
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
    case req: EmailRequest ⇒
      log.info(s"Upserting request with id ${req.id} to database")
      emailsDao.persistRequest(req)
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
