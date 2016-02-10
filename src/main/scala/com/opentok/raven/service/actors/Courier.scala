package com.opentok.raven.service.actors

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.pattern._
import akka.util.Timeout
import com.opentok.raven.dal.components.EmailRequestDao
import com.opentok.raven.model.{Email, EmailRequest, Receipt}

import scala.concurrent.{ExecutionContext, Future, Promise}
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

  val daoService: ActorRef
  val emailProvider: ActorRef

  /**
   * Persists request to database and logs op results
   */
  def persistRequest(req: EmailRequest): Future[Any] = {
    log.info(s"sending request for dao service to persist $req")
    daoService.ask(req).andThen {
      case Success(i) ⇒
        log.info(s"Successfully persisted request with id ${req.id} to database")
      case Failure(e) ⇒
        log.error(e, s"There was an error when persisting request with id ${req.id} to database")
    }
  }

  def persistRequests(reqs: List[EmailRequest]): Future[Any] = {
    if (reqs.isEmpty) Future.failed(new Exception("trying to persist request but list is empty"))
    Future.sequence[Any, List](reqs.map(persistRequest))
  }


  /**
   * Asks email provider to send email and recovers
   * exception into a receipt if there was one
   */
  def send(id: Option[String], email: Email): Future[Receipt] = {
    log.info(s"sending email via email provider in path ${emailProvider.path} with timeout $timeout")
    emailProvider.ask(email).mapTo[Receipt]
      .map(_.copy(requestId = id))
      .recover {
        case e: Exception ⇒
          val msg = s"There was a problem when processing email request with id $id"
          log.error(e, msg)
          Receipt.error(e, message = msg, requestId = id)
      }
  }

  implicit class PimpedFutureReceipt(f: Future[Receipt]) {

    //applies the side-effecting function to the result of this future, and returns
    //a new future with the flattened result of the passed future
    //basically it completes promise with initial result when inner future is done
    def flatAndThen(pf: PartialFunction[Try[Receipt], Future[_]])(implicit executor: ExecutionContext): Future[Receipt] = {
      val p = Promise[Receipt]()
      f onComplete { r ⇒
        try {
          //supply value of this future to pf
          pf apply r onComplete { _ ⇒
            //if everything goes well complete promise with initial value of the future
            p complete r
          }
        } catch {
          //if something fails, complete promise with initial value
          case t: Throwable ⇒ p complete r
        }
      }
      p.future
    }

    /**
     * Persist results of this request as a side effect of the given future,
     * thus with no impact in end result, assuming that the given prom of
     * receipt is successful when email was processed correctly in provider
     * and unsuccessfully if and only if there was a failure when processing email
     */
    def andThenPersistResult(req: EmailRequest): Future[Receipt] =
      f.flatAndThen {
        //email was processed successfully by provider
        case Success(rec) if rec.success ⇒
          persistRequest(req.copy(status = Some(EmailRequest.Succeeded)))
        //email was NOT processed successfully by provider
        case Success(rec) ⇒
          persistRequest(req.copy(status = Some(EmailRequest.Failed)))
        case Failure(e) ⇒
          persistRequest(req.copy(status = Some(EmailRequest.Failed)))
      }

    def andThenPersistResult(reqs: List[EmailRequest]): Future[Receipt] =
      f.flatAndThen {
        case Success(rec) if rec.success ⇒
          Future.sequence[Any, List](reqs.map(req ⇒
            persistRequest(req.copy(status = Some(EmailRequest.Succeeded)))))
        case Success(rec) ⇒
          Future.sequence[Any, List](reqs.map(req ⇒
            persistRequest(req.copy(status = Some(EmailRequest.Failed)))))
        case Failure(e) ⇒
          Future.sequence[Any, List](reqs.map(req ⇒
            persistRequest(req.copy(status = Some(EmailRequest.Failed)))))
      }
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
