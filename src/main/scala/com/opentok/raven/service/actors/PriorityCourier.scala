package com.opentok.raven.service.actors

import akka.actor.{ActorRef, Actor, ActorLogging}
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.HttpResponse
import com.opentok.raven.dal.components.EmailRequestDao
import akka.pattern._
import com.opentok.raven.model.{EmailRequest, Receipt}

import scala.util.{Failure, Success}

/**
 * Upon receiving an email request, this actor will attempt to forward it
 * to sendgrid straight away and persist the request results after delivering
 * the receipt to the requester.
 *
 * @param emails email requests data access object
 */

//TODO
//TODO
//TODO
//TODO
//TODO
//TODO
//TODO
//TODO
//TODO make sure to use receiptIds
class PriorityCourier(emails: EmailRequestDao) extends Actor with ActorLogging {

  implicit val logger: LoggingAdapter = log

  import context.dispatcher

  val sendgridActor: ActorRef = context.system.deadLetters

  //uuid -> n retries
  val retries: scala.collection.mutable.Map[String, Int] = scala.collection.mutable.Map.empty

  override def receive: Receive = {
    case req: EmailRequest ⇒
      log.info(s"Received request with id ${req.id}")
      log.debug("Received {}", req)
      sendgridActor.ask(req).mapTo[HttpResponse].map {
        case response if response.status.isSuccess() ⇒
          Receipt(success = true)
        case response ⇒ Receipt(success = false,
          response.status.defaultMessage(), errors = response.status.reason :: Nil)
      }.recover {
        case e: Exception ⇒
          Receipt(success = false, "There was a problem when processing email request",
            errors = e.getCause.getMessage :: e.getMessage :: Nil)
      }.andThen {
        //persist request
        case Success(receipt) if receipt.success ⇒
          log.info(s"Successfully processed request with id ${req.id}")
          emails.persistRequest(req).onComplete {
            case Success(i) ⇒
              log.info(s"Successfully persisted request with id ${req.id} to database")
            case Failure(e) ⇒
              log.error(e, s"There was an error when persisting request with id ${req.id} to database")
          }
        case Failure(e) ⇒
          emails.persistRequest(req)
          log.error(e, s"There was a problem when processing request with id ${req.id}")
      }
      emails.persistRequest(req).map { i ⇒
        log.info(s"Saved $req with id '$reqNo' successfully into database")
        Receipt.success
      }.recover {
        case e: Exception ⇒
          val msg = "Something went wrong when trying to persist relay request"
          log.error(e, msg)
          Receipt.error(e, msg)
      }
  }
}
