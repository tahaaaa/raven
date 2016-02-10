package com.opentok.raven.service.actors

import akka.actor._
import akka.event.LoggingAdapter
import com.opentok.raven.dal.components.EmailRequestDao
import com.opentok.raven.model.{EmailRequest, Receipt, Requestable}
import com.opentok.raven.service.actors.MonitoringActor.PendingEmailsCheck

import scala.collection.TraversableLike
import scala.concurrent.duration._
import scala.util.Try

/**
 * Email Service Supervisor. This actor will take an arbitrary [[akka.actor.Props]],
 * manually instantiate and supervise a pool of actors and load balance new requests to it.
 *
 * This actor implements a safe retry mechanism which is triggered when receiving unsuccessful
 * receipts from its supevisees, of requests that appear as  [[com.opentok.raven.model.EmailRequest.Failed]]
 * or [[com.opentok.raven.model.EmailRequest.Pending]] in the database.
 *
 * Note that this actor doesn't use [[akka.pattern.ask]] and this is to make its internal state
 * thread safe (the mutable value that stores the pending requests).
 *
 * @param superviseeProps Actor configuration object used to instantiate new supervisees
 * @param pool number of actors to create and monitor using props
 * @param emailDao data access object to emails table in db to verify certain conditions
 * @param MAX_RETRIES maximum number of retries allowed per failed request
 * @param DEFERRER Time to wait for next retry (will be multiplied by current reply no.)
 */
class EmailSupervisor(superviseeProps: Props, pool: Int,
                      emailDao: EmailRequestDao, MAX_RETRIES: Int, DEFERRER: Int)
  extends Actor with ActorLogging {

  case class SupervisedRequest(request: Requestable, requester: ActorRef, handler: ActorRef)

  import context.dispatcher

  override def preStart(): Unit = {
    log.info(s"Supervisor up and monitoring $superviseeProps with a pool of $pool " +
      s"actors and with a max number of retries of $MAX_RETRIES")
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
    case (supervisedRequest, retries) if retries < MAX_RETRIES ⇒
      //check that email was not really sent and only if status in db is not completed, retry again
      supervisedRequest.request.id.map { id ⇒
        emailDao.retrieveRequest(id).map {
          case Some(req) ⇒ req.status match {
            case Some(status) if status == EmailRequest.Failed || status == EmailRequest.Pending ⇒
              log.warning(s"It looks like request with id $id is failed or still pending. Retrying now..")
              context.system.scheduler.scheduleOnce((DEFERRER * retries).seconds, self, supervisedRequest.request)
            case Some(status) if status == EmailRequest.Succeeded ⇒
              val msg = s"Request with id $id appears to have succeded. Aborting retry mechanism"
              supervisedRequest.requester ! receipt.getOrElse(Receipt(success = true,
                requestId = Some(id), message = Some(msg)))
              log.warning(msg)
            case None ⇒
              val msg = s"Request with id $id status' is not set in the database. Aborting retry mechanism!"
              supervisedRequest.requester ! receipt.getOrElse(Receipt(success = false,
                requestId = Some(id), message = Some(msg)))
              log.warning(msg)
          }
          case None ⇒
            val msg = s"Weird. Request with id $id was not persisted first time. Aborting retry mechanism!"
            supervisedRequest.requester ! receipt.getOrElse(Receipt(success = false,
              requestId = Some(id), message = Some(msg)))
            log.warning(msg)
        }.recover {
          //there was an exception when retrieving request from db
          case e: Exception ⇒
            val msg = "There was an error when retrieving request from db. Skipping retry mechanism"
            log.error(e, msg)
            supervisedRequest.requester ! receipt.map(
              r ⇒ r.copy(errors = msg + ":" + e.getMessage :: r.errors)
            ).getOrElse(Receipt(success = false, requestId = Some(id), message = Some(msg),
              errors = msg + ":" + e.getMessage :: Nil))
        }
      }.getOrElse {
        log.error("Could not use retry mechanism because request doesn't have a request id")
      }

    case (supervisedRequest, retries) ⇒
      //keeps request_id in map
      val msg = s"Retried request ${supervisedRequest.request} $retries times unsuccessfully"
      log.error(msg)
      lazy val err = Receipt.error(new Exception(s"Maximum number of retries reached $MAX_RETRIES"), msg, requestId = supervisedRequest.request.id)
      val combined = receipt.map(r ⇒ Receipt.reduce(err :: r :: Nil)).getOrElse(err)
      supervisedRequest.requester ! combined
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
      //randomly pick one of the supervisees
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
    case PendingEmailsCheck ⇒
      log.info("Checking pending emails now..")
      val stats = pending.toMap.map(kv ⇒ kv._1.request.id.getOrElse("") → kv._2) //make immutable before sending
      sender() ! stats

    case reqs: TraversableLike[_, _] ⇒
      log.info("Received collection of requests.. Attempting to load balance to supervisees")
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
      log.info("Received request.. Attempting to assign now to a supervisee")
      superviseRequest(req, sender())

    case rec: Receipt if rec.success ⇒
      log.info("Received successful receipt from supervisees. Attempting to forward back to requester...")
      rec.requestId.map { id ⇒
        pending.find(_._1.request.id.getOrElse("") == id).map { request ⇒
          //reply to requester
          request._1.requester ! rec
          //remove from control map
          pending.remove(request._1).get
        }.getOrElse(logNotFound(s"Request with Id $id not found in control map"))
      }.getOrElse(logNotFound("Receipt did not contain a requestId"))

    case rec: Receipt if !rec.success ⇒
      log.info("Received UNSUCCESSFUL receipt from supervisees. Attempting to forward back to requester...")
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
