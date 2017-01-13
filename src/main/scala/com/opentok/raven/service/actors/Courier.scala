package com.opentok.raven.service.actors

import akka.actor.{Actor, ActorRef}
import akka.pattern._
import akka.util.Timeout
import com.opentok.raven.RavenLogging
import com.opentok.raven.dal.components.EmailRequestDao
import com.opentok.raven.model._

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

/**
 * Common methods used by [[com.opentok.raven.service.actors.PriorityCourier]] and
 * [[com.opentok.raven.service.actors.CertifiedCourier]]
 */
trait Courier extends RavenLogging {
  this: Actor ⇒

  import context.dispatcher

  val emailsDao: EmailRequestDao
  implicit val timeout: Timeout

  val daoService: ActorRef
  val provider: Provider

  @throws[Exception](classOf[Exception])
  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    message match {
      //send receipt to supervisor so that it can retry
      case Some(req: Requestable) ⇒ context.parent ! Receipt.error(reason, "courier crashed", req.id)
      case _ ⇒ log.error(reason, s"${self.path} could not recover: message was not a requestable")
    }
    context.children foreach { child ⇒
      context.unwatch(child)
      context.stop(child)
    }
    postStop()
  }

  /**
   * Persists request to database and logs op results
   */
  def persistRequest(req: EmailRequest)(implicit ctx: RequestContext): Future[Any] =
    daoService.ask(ctx.copy(req = req))

  def persistRequests(reqs: List[EmailRequest])(implicit ctx: RequestContext): Future[Any] = {
    if (reqs.isEmpty) Future.failed(new Exception("trying to persist empty list of requests"))
    Future.sequence[Any, List](reqs.map(persistRequest))
  }


  /**
   * Asks email provider to send email and recovers
   * exception into a receipt if there was one
   */
  def send(id: Option[String], email: Email)(implicit ctx: RequestContext): Future[Receipt] = {
    provider.send(email).mapTo[Receipt].map(_.copy(requestId = id))
      .recover {
        case e: Exception ⇒
          val msg = s"there was a problem when processing email request with id $id"
          Receipt.error(e, message = msg, requestId = id)
      }
  }

  implicit class PimpedFutureReceipt(f: Future[Receipt]) {

    //applies the side-effecting function to the result of this future, and returns
    //a new future with the flattened result of the passed future
    //basically it completes promise with initial result when inner future is done
    def flatAndThen(pf: PartialFunction[Try[Receipt], Future[_]])
                   (implicit executor: ExecutionContext): Future[Receipt] = {
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
    def andThenPersistResult(req: EmailRequest)(implicit ctx: RequestContext): Future[Receipt] =
      f.flatAndThen {
        //email was processed successfully by provider
        case Success(rec) if rec.success && rec.errors.isEmpty ⇒
          persistRequest(req.copy(status = Some(EmailRequest.Succeeded)))
        //email was filtered out: prd flag is off and didn't match regex
        case Success(rec) if rec.success ⇒
          persistRequest(req.copy(status = Some(EmailRequest.Filtered)))
        //email was NOT processed successfully by provider
        case Success(rec) ⇒
          persistRequest(req.copy(status = Some(EmailRequest.Failed)))
        case Failure(e) ⇒
          persistRequest(req.copy(status = Some(EmailRequest.Failed)))
      }

    def andThenPersistResult(reqs: List[EmailRequest])(implicit ctx: RequestContext): Future[Receipt] =
      f.flatAndThen {
        case Success(rec) if rec.success && rec.errors.isEmpty ⇒
          Future.sequence[Any, List](reqs.map(req ⇒
            persistRequest(req.copy(status = Some(EmailRequest.Succeeded)))))
        case Success(rec) if rec.success && reqs.length == 1 ⇒
          Future.sequence[Any, List](reqs.map(req ⇒
          persistRequest(req.copy(status = Some(EmailRequest.Filtered)))))
        case Success(rec) if rec.success ⇒
          Future.sequence[Any, List](reqs.map(req ⇒
          persistRequest(req.copy(status = Some(EmailRequest.PartiallyFiltered)))))
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
class RequestPersister(emailsDao: EmailRequestDao) extends Actor with RavenLogging {

  import context.dispatcher

  def getErrorMsg(req: Any) = s"received unacceptable request: $req"

  val singleMessageRecv: PartialFunction[Any, Future[Int]] = {
    case ctx@RequestContext(req: EmailRequest, traceId) ⇒
      emailsDao.persistRequest(req)(context.dispatcher, ctx)
    case req ⇒ Future.failed(new Exception(getErrorMsg(req)))
  }

  def receive: Actor.Receive = {
    case msg ⇒ singleMessageRecv(msg) pipeTo sender()
  }
}
