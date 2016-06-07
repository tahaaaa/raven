package com.opentok.raven.service.actors

import akka.actor._
import com.opentok.raven.RavenLogging
import com.opentok.raven.dal.components.EmailRequestDao
import com.opentok.raven.model.{EmailRequest, Receipt, Requestable}

import scala.concurrent.duration._
import scala.util.{Failure, Success}

/**
 * Email Service Supervisor. This actor will take an arbitrary [[akka.actor.Props]],
 * manually instantiate and supervise a pool of actors and load balance new requests to it.
 *
 * This actor implements a safe retry mechanism which is triggered when receiving unsuccessful
 * receipts from its supevisees, of requests that appear as  [[com.opentok.raven.model.EmailRequest.Failed]]
 * or [[com.opentok.raven.model.EmailRequest.Pending]] in the database, or when one of the
 * supervisees crashes.
 *
 * @param superviseeProps Actor configuration object used to instantiate new supervisees
 * @param nSupervisees number of actors to create and monitor using props
 * @param emailDao data access object to emails table in db to verify certain conditions
 * @param retries maximum number of retries allowed per failed request
 * @param deferrer Time to wait for next retry (will be multiplied by current reply no.)
 */
class EmailSupervisor(superviseeProps: Props, nSupervisees: Int,
                      emailDao: EmailRequestDao, retries: Int, deferrer: Int)
  extends Actor with RavenLogging {

  case class SupervisedRequest(request: Requestable, requester: ActorRef)

  import context.dispatcher

  override def preStart(): Unit = {
    debug(log, "Supervisor up and monitoring {} with a pool of {} actors and with a max number of retries of {}",
      superviseeProps, nSupervisees, retries)
  }

  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
    case e: Exception ⇒ SupervisorStrategy.Restart
    case e: Throwable ⇒ SupervisorStrategy.Escalate
  }

  val poolRange = 0 until nSupervisees

  val random = new scala.util.Random

  val supervisee: Vector[ActorRef] = poolRange.foldLeft(Vector.empty[ActorRef]) { (vec, i) ⇒
    val routee = context.actorOf(superviseeProps, s"${superviseeProps.actorClass().getSimpleName}-$i")
    vec :+ routee
  }

  //uuid -> n tries
  val pending: scala.collection.mutable.Map[SupervisedRequest, Int] = scala.collection.mutable.Map.empty

  /**
   * Checks if request has been seen already by this actor and updates
   * number of retries for it.
   */
  def superviseRequest(req: Requestable, requester: ActorRef) {
    val supervisedMaybe = pending.find(_._1.request.id == req.id)
    val id = req.id.get

    val ret: Int = if (supervisedMaybe.isDefined) {
      //already registered, update retries
      val request = supervisedMaybe.get
      val r = request._2 + 1
      pending.update(request._1, r)
      r
    } else {
      // first try
      val id = req.id.get
      pending.update(SupervisedRequest(req, requester), 1)
      1
    }

    trace(log, id, SuperviseRequest, Variation.Attempt, Some(s"supervising request '$id'; tries: $ret/$retries"))

    //randomly pick one of the supervisees
    val handler = supervisee(poolRange(random nextInt nSupervisees))
    handler ! req
  }

  val logNotFound = { reason: String ⇒
    log.error(s"could not send receipt back to original requester. $reason")
  }

  val eventBus = context.system.eventStream

  override def receive: Receive = {

    case req: Requestable ⇒ superviseRequest(req, sender())

    //forward receipt back to requester
    case rec: Receipt if rec.success ⇒
      rec.requestId.map { id ⇒
        trace(log, id, SuperviseRequest, Variation.Success,
          Some(s"completed request with id '${rec.requestId}' successfully"))
        pending.find(_._1.request.id.get == id).map { request ⇒
          //reply to requester
          request._1.requester ! rec
          //remove from control map
          pending.remove(request._1).get
        }.getOrElse(logNotFound(s"request with id '$id' not found"))
      }.getOrElse(logNotFound("receipt did not contain a requestId"))

    //try to retry
    case receipt: Receipt if !receipt.success ⇒
      receipt.requestId.map { id ⇒
        trace(log, id, SuperviseRequest, Variation.Failure(new Exception(s"there was an error when processing request '$id'${receipt.errors.headOption.map(" :" + _).getOrElse("")}")))
        pending.find(_._1.request.id.get == id).map {
          case (supervisedRequest, ret) if ret < retries ⇒
            //check that email was not really sent and only if status in db is not completed, retry again
            supervisedRequest.request.id.map { id ⇒
              emailDao.retrieveRequest(id).map {
                case Some(req) ⇒ req.status match {
                  //legitimate retry
                  case Some(status) if status == EmailRequest.Failed || status == EmailRequest.Pending ⇒
                    val in = deferrer.seconds
                    //schedule send message to self with request in n seconds
                    context.system.scheduler.scheduleOnce(deferrer.seconds, self, supervisedRequest.request)
                  //already succeeded
                  case Some(status) if status == EmailRequest.Succeeded ⇒
                    val msg = s"aborting retry mechanism: request with id '$id' appears to have succeeded already"
                    supervisedRequest.requester ! receipt
                    eventBus.publish(receipt)
                    log.error(msg)
                  case None ⇒
                    val msg = s"aborting retry mechanism: request with id '$id' not set in the db"
                    supervisedRequest.requester ! receipt
                    eventBus.publish(receipt)
                    log.error(msg)
                }
                case None ⇒
                  val msg = s"aborting retry mechanism: request with id '$id' was not persisted first time"
                  supervisedRequest.requester ! receipt
                  eventBus.publish(receipt)
                  log.error(msg)
              }.recover {
                //there was an exception when retrieving request from db
                case e: Exception ⇒
                  val msg = "aborting retry mechanism: there was an error when retrieving request from db"
                  //add error to list of errors
                  val finalReceipt = receipt.copy(errors = s"$msg: $e" :: receipt.errors)

                  eventBus.publish(finalReceipt)

                  supervisedRequest.requester ! finalReceipt
              }
            }.getOrElse(log.error("aborting retry mechanism: request doesn't have a request id"))

          case (supervisedRequest, ret) ⇒
            //remove request from pending
            pending.remove(supervisedRequest)
            val msg = s"request with id '${supervisedRequest.request.id.get}' exhausted $ret retries and is permanently in failed state"
            val e = new Exception(msg)
            trace(log, id, SuperviseRequest, Variation.Failure(e), Some(msg))
            val err = Receipt.error(e, msg, requestId = supervisedRequest.request.id)
            val combined = Receipt.reduce(err :: receipt :: Nil)
            supervisedRequest.requester ! combined
            eventBus.publish(combined)
        }
          .getOrElse(logNotFound(s"request with id '$id' not found"))
      }.getOrElse(logNotFound("receipt did not contain a requestId"))

    case anyElse ⇒ warning(log, "not an acceptable request: {}", anyElse)
  }
}
