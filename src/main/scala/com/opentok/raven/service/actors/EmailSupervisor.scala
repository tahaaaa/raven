package com.opentok.raven.service.actors

import akka.actor._
import akka.event.LoggingAdapter
import com.opentok.raven.dal.components.EmailRequestDao
import com.opentok.raven.model.{Requestable, EmailRequest, Receipt}
import com.opentok.raven.service.actors.MonitoringActor.InFlightEmailsCheck

import scala.collection.TraversableLike
import scala.concurrent.duration._
import scala.util.Try

/**
 * Email Service Supervisor
 *
 * @param superviseeProps Actor configuration object used to instantiate new supervisees
 * @param pool number of actors to create and monitor using props
 * @param emailDao data access object to emails table in db to verify certain conditions
 * @param maxRetries maximum number of retries allowed per failed request
 */
class EmailSupervisor(superviseeProps: Props, pool: Int, emailDao: EmailRequestDao, maxRetries: Int)
  extends Actor with ActorLogging {

  case class SupervisedRequest(request: Requestable, requester: ActorRef, handler: ActorRef)

  import context.dispatcher

  override def preStart(): Unit = {
    log.info(s"Supervisor up and monitoring $superviseeProps with a pool of $pool actors")
  }

  /* The policy to apply when a supervisee crashes */
  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
    case anyEx if sender().path.name.contains(superviseeProps.actorClass().getSimpleName) ⇒
      val routee = sender()
      pending.find(_._1.handler == routee)
        .map(retryOrReplyBack(None))
        .getOrElse(logNotFound("Routee not handling any in flight requests. Resuming.."))
      SupervisorStrategy.Resume
    case e: Exception ⇒ SupervisorStrategy.Escalate //should never happen
    case e: Throwable ⇒ SupervisorStrategy.Escalate
  }

  implicit val logger: LoggingAdapter = log

  val poolN = 0 until pool

  val rdm = new scala.util.Random

  val supervisee: Vector[ActorRef] = poolN.foldLeft(Vector.empty[ActorRef]) { (vec, i) ⇒
    val routee = context.actorOf(superviseeProps, s"${superviseeProps.actorClass().getSimpleName}-$i")
    context.watch(routee)
    vec :+ routee
  }

  //uuid -> n tries
  val pending: scala.collection.mutable.Map[SupervisedRequest, Int] = scala.collection.mutable.Map.empty

  def retryOrReplyBack(receipt: Option[Receipt]): PartialFunction[(SupervisedRequest, Int), Unit] = {
    case (supervisedRequest, retries) if retries < retries ⇒
      //check that email was not really sent and only if status in db is not completed, retry again
      supervisedRequest.request.id.map { id ⇒
        emailDao.retrieveRequest(id).map {
          case Some(req) ⇒ req.status match {
            case Some(status) if status == EmailRequest.Failed || status == EmailRequest.Pending ⇒
              log.warning(s"It looks like request with id $id is failed or still pending. Retrying now..")
              context.system.scheduler.scheduleOnce((5 * retries).seconds, self, supervisedRequest.request)
            case Some(status) if status == EmailRequest.Succeeded ⇒
              val msg = "Request with id appears to have succeded. Aborting retry mechanism"
              supervisedRequest.requester ! receipt.getOrElse(Receipt(success = true,
                requestId = Some(id), message = Some(msg)))
              log.warning(msg)
            case None ⇒
              val msg = "Request not found in database. Unable to determine status. Aborting retry mechanism!"
              supervisedRequest.requester ! receipt.getOrElse(Receipt(success = false,
                requestId = Some(id), message = Some(msg)))
              log.error(msg)
          }
          case None ⇒
            val msg = s"Weird. Request with id $id was not persisted first time. Aborting retry mechanism!"
            supervisedRequest.requester ! receipt.getOrElse(Receipt(success = false,
              requestId = Some(id), message = Some(msg)))
            log.error(msg)
        }
      }.getOrElse {
        log.error("Could use retry mechanism because request doesn't have a request id")
      }

    case (supervisedRequest, retries) ⇒
      //keeps request_id in map
      val msg = s"Retried request ${supervisedRequest.request} $retries times unsuccessfully"
      log.error(msg)
      supervisedRequest.requester ! Receipt.error(new Exception(s"Maximum number of retries reached"), msg,
        requestId = supervisedRequest.request.id)
  }

  def superviseRequest(req: Requestable, requester: ActorRef) {
    val handler: ActorRef = pending.find(_._1.request.id == req.id).map {
      //already registered, update retries
      request ⇒
        log.info(s"EmailRequest already registered, currently with ${request._2} retries")
        pending.update(request._1, request._2 + 1)
        request._1.handler
    }.getOrElse {
      // first try
      log.info(s"Supervising EmailRequest with id ${req.id}")
      val routee = Try(supervisee(poolN(rdm nextInt pool))).getOrElse(supervisee.head)
      pending.update(SupervisedRequest(req, requester, routee), 1)
      routee
    }
    handler ! req
  }

  val logNotFound = { reason: String ⇒
    log.error(s"Could not send receipt back to original requester. $reason")
  }

  override def receive: Receive = {

    //monitoring
    case InFlightEmailsCheck ⇒
      val stats = pending.toMap.map( kv ⇒ kv._1.request.id.getOrElse("") → kv._2) //make immutable before sending
      sender() ! stats

    case reqs: TraversableLike[_, _] ⇒
      val processed = reqs.foldLeft((0, 0)) {
        case ((accepted, rejected), req: Requestable) ⇒
          superviseRequest(req, sender())
          (accepted + 1, rejected)
        case ((accepted, rejected), smtg) ⇒
          val msg = s"Not an acceptable request $smtg"
          log.warning(msg)
          (accepted, rejected + 1)
      }
      sender() ! Receipt(success = processed._2 == 0,
        message = Some(s"${processed._1} requests were accepted and ${processed._2} were rejected"))

    case req: Requestable ⇒
      superviseRequest(req, sender())

    case rec: Receipt if rec.success ⇒
      rec.requestId.map { id ⇒
        pending.find(_._1.request.id.getOrElse("") == id).map { request ⇒
          //reply to requester
          request._1.requester ! rec
          //remove from control map
          pending.remove(request._1).get
        }.getOrElse(logNotFound(s"Request with Id $id not found in control map"))
      }.getOrElse(logNotFound("Receipt did not contain a requestId"))

    case rec: Receipt if !rec.success ⇒
      rec.requestId.map { id ⇒
        pending.find(_._1.request.id.getOrElse("") == id)
          .map(retryOrReplyBack(Some(rec)))
          .getOrElse(logNotFound("This is impossible! Unsuccessful receipt has a receipt id but id not present in control map.."))
      }.getOrElse(logNotFound(s"Unable to retry because receipt doesn't have a requestId!! Dropping $rec"))

    case anyElse ⇒
      log.warning(s"Not an acceptable request $anyElse")

    //    case Terminated(routee) ⇒ not handled to force trigger DeathPactException

  }
}
