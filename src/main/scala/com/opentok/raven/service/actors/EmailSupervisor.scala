package com.opentok.raven.service.actors

import akka.actor._
import akka.event.LoggingAdapter
import com.opentok.raven.GlobalConfig
import com.opentok.raven.model.{EmailRequest, Receipt}

import scala.concurrent.duration._

/**
 * Service Supervisor
 * @param superviseeProps Actor configuration object used to instantiate new [[Actor]]
 */
class EmailSupervisor(superviseeProps: Props) extends Actor with ActorLogging {

  case class SupervisedRequest(request: EmailRequest, requester: ActorRef)

  /* The policy to apply when a supervisee crashes */
  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
    case e: Exception ⇒ SupervisorStrategy.Restart
  }

  implicit val logger: LoggingAdapter = log

  val supervisee = context.actorOf(superviseeProps)

  //uuid -> n tries
  val inFlight: scala.collection.mutable.Map[SupervisedRequest, Int] = scala.collection.mutable.Map.empty

  def superviseRequest(req: EmailRequest, requester: ActorRef) {
    inFlight.find(_._1.request.id == req.id).map {
      //already registered, update retries
      request ⇒
        log.info(s"EmailRequest already registered, currently with ${request._2} retries")
        inFlight.update(request._1, request._2 + 1)
    }.getOrElse {
      // first try
      log.info(s"Supervising EmailRequest with id ${req.id}")
      inFlight.update(SupervisedRequest(req, requester), 1)
    }
    supervisee ! req
  }

  def logNotFound(reason: String): Unit = {
    log.warning(s"Could not send receipt back to original requester. $reason")
  }

  override def receive: Receive = {

    case reqs: List[_] ⇒
      val processed = reqs.foldLeft((0, 0)) {
        case ((accepted, rejected), req: EmailRequest) ⇒
          superviseRequest(req, sender())
          (accepted + 1, rejected)
        case ((accepted, rejected), smtg) ⇒
          val msg = s"Not an acceptable request $smtg"
          log.warning(msg)
          (accepted, rejected + 1)
      }
      sender() ! Receipt(success = processed._2 == 0,
        message = Some(s"${processed._1} requests were accepted and ${processed._2} were rejected"))

    case req: EmailRequest ⇒
      superviseRequest(req, sender())

    case rec: Receipt if rec.success ⇒
      rec.requestId.map { id ⇒
        inFlight.find(_._1.request.id == id).map { request ⇒
          //reply to requester
          request._1.requester ! rec
          //remove from control map
          inFlight.remove(request._1).get
        }.getOrElse(logNotFound(s"Request with Id $id not found in control map"))
      }.getOrElse(logNotFound("Receipt did not contain a requestId"))

    case rec: Receipt if !rec.success ⇒
      rec.requestId.map { id ⇒
        inFlight.find(_._1.request.id == id).map {
          case (supervisedRequest, retries) if retries < GlobalConfig.MAX_RETRIES ⇒
            context.system.scheduler.scheduleOnce((5 * retries).seconds, self, supervisedRequest.request)
          case (supervisedRequest, retries) ⇒
            val msg = s"Retried request ${supervisedRequest.request} ${GlobalConfig.MAX_RETRIES} times unsuccessfully"
            log.error(msg)
            supervisedRequest.requester ! Receipt.error(new Exception(s"Maximum number of retries reached"), msg,
              requestId = Some(supervisedRequest.request.id))
        }.getOrElse(log.error("This is impossible! Unsuccessful receipt has a receipt id but id not present in control map.."))
      }.getOrElse(log.error(s"Unable to retry because receipt doesn't have a requestId!! Dropping $rec"))
  }
}
