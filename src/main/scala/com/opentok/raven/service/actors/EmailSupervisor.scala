package com.opentok.raven.service.actors

import akka.actor._
import akka.event.LoggingAdapter
import com.opentok.raven.dal.components.EmailRequestDao
import com.opentok.raven.model.{EmailRequest, Receipt}
import com.opentok.raven.service.actors.MonitoringActor.InFlightEmailsCheck

import scala.concurrent.duration._

/**
 * Service Supervisor
 * TODO document
 *
 * @param superviseeProps Actor configuration object used to instantiate new [[Actor]]
 * @param pool number of actors to supervise and create using props
 * @param emailDao
 */
class EmailSupervisor(superviseeProps: Props, pool: Int, emailDao: EmailRequestDao, maxRetries: Int) extends Actor with ActorLogging {

  case class SupervisedRequest(request: EmailRequest, requester: ActorRef, handler: ActorRef)

  import context.dispatcher

  override def preStart(): Unit = {
    log.info(s"Supervisor up and monitoring $superviseeProps with a pool of $pool actors")
  }

  /* The policy to apply when a supervisee crashes */
  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
    case anyEx if sender().path.name.contains("supervisee") ⇒
      val routee = sender()
      inFlight.find(_._1.handler == routee)
        .map(retryOrReplyBack(None))
        .getOrElse(logNotFound("Routee not handling any in flight requests. Resuming.."))
      SupervisorStrategy.Resume
    case e: Exception ⇒ SupervisorStrategy.Escalate //should never happen
    case e: Throwable ⇒ SupervisorStrategy.Escalate
  }

  implicit val logger: LoggingAdapter = log

  val poolN = 0 until pool

  val supervisee: Vector[ActorRef] = poolN.foldLeft(Vector.empty[ActorRef]) { (vec, i) ⇒
    val routee = context.actorOf(superviseeProps, s"${superviseeProps.actorClass().getSimpleName}-$i")
    context.watch(routee)
    vec :+ routee
  }

  //uuid -> n tries
  val inFlight: scala.collection.mutable.Map[SupervisedRequest, Int] = scala.collection.mutable.Map.empty

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

  def superviseRequest(req: EmailRequest, requester: ActorRef) {
    val handler: ActorRef = inFlight.find(_._1.request.id == req.id).map {
      //already registered, update retries
      request ⇒
        log.info(s"EmailRequest already registered, currently with ${request._2} retries")
        inFlight.update(request._1, request._2 + 1)
        request._1.handler
    }.getOrElse {
      // first try
      log.info(s"Supervising EmailRequest with id ${req.id}")
      val routee = supervisee(poolN(new scala.util.Random nextInt pool))
      inFlight.update(SupervisedRequest(req, requester, routee), 1)
      routee
    }
    handler ! req
  }

  val logNotFound = { reason: String ⇒
    log.debug("Control map {}", inFlight)
    log.error(s"Could not send receipt back to original requester. $reason")
  }

  override def receive: Receive = {

    //monitoring
    case InFlightEmailsCheck ⇒
      val stats = inFlight.toMap.map( kv ⇒ kv._1.request.id.getOrElse("") → kv._2) //make immutable before sending
      sender() ! stats

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
        inFlight.find(_._1.request.id.getOrElse("") == id).map { request ⇒
          //reply to requester
          request._1.requester ! rec
          //remove from control map
          inFlight.remove(request._1).get
        }.getOrElse(logNotFound(s"Request with Id $id not found in control map"))
      }.getOrElse(logNotFound("Receipt did not contain a requestId"))

    case rec: Receipt if !rec.success ⇒
      rec.requestId.map { id ⇒
        inFlight.find(_._1.request.id.getOrElse("") == id)
          .map(retryOrReplyBack(Some(rec)))
          .getOrElse(logNotFound("This is impossible! Unsuccessful receipt has a receipt id but id not present in control map.."))
      }.getOrElse(logNotFound(s"Unable to retry because receipt doesn't have a requestId!! Dropping $rec"))

    case anyElse ⇒
      log.warning(s"Not an acceptable request $anyElse")

    //    case Terminated(routee) ⇒ not handled to force trigger DeathPactException

  }
}
