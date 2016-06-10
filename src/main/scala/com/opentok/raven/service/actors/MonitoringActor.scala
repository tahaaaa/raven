package com.opentok.raven.service.actors

import akka.actor._
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import com.opentok.raven.RavenLogging
import com.opentok.raven.model.Receipt
import com.opentok.raven.service.actors.MonitoringActor.{ComponentHealthCheck, FailedEmailsCheck}
import com.typesafe.config.ConfigFactory
import slick.driver.JdbcProfile
import slick.jdbc.JdbcBackend

import scala.collection.mutable
import scala.concurrent.{Future, Await}
import scala.util.Try

object MonitoringActor {

  case class ComponentHealthCheck(component: String)

  case object FailedEmailsCheck

}

/**
 * Handles db connectivity check, service uptime check
 * and failed/pending emails with its retries.
 *
 * @param certifiedService certified actor/router instance
 * @param priorityService priority actor/router instance
 * @param testDal function to use to test DAL
 * @param t timeout to apply to dal/service checks
 */
class MonitoringActor(certifiedService: ActorRef, priorityService: ActorRef,
                      testDal: () ⇒ Future[_], t: Timeout)
  extends Actor with RavenLogging {

  import context.dispatcher

  implicit val timeout: Timeout = t
  val config = ConfigFactory.load()
  val dbHost = config.getString("raven.database.properties.serverName")
  val dbPort = config.getInt("raven.database.properties.portNumber")

  //subscribe to receipt to monitor failed requests
  context.system.eventStream.subscribe(self, classOf[Receipt])

  val MAX_FAILED = 200
  val failed = mutable.Queue.empty[Receipt]

  override def receive: Receive = {

    //from eventBus
    case r: Receipt ⇒
      if (!r.success) {
        failed.enqueue(r)
        //prevent memory leak
        if (failed.length > MAX_FAILED) {
          val dequeued = failed.dequeue()
          warning(log, "number of failed emails is over {}! dropping {}", MAX_FAILED, dequeued)
        }
      }

    //clone to immutable and send
    case check@FailedEmailsCheck ⇒ sender() ! failed.toVector

    case ComponentHealthCheck("service") ⇒
      (for {
        idCertified ← certifiedService ? Identify("certified")
        idPriority ← priorityService ? Identify("priority")
      } yield Receipt.success(Some("OK"), None)) recover {
        case e: Exception ⇒ Receipt.error(e, "Unable to establish communication with services")
      } pipeTo sender()

    case ComponentHealthCheck("dal") ⇒
      testDal().map(_ ⇒ Receipt.success).recover {
        case e: Exception ⇒
          Receipt.error(e, "Unable to establish communication with " +
            s"db $dbHost:$dbPort using ")
      } pipeTo sender()

    case ComponentHealthCheck(_) ⇒ sender() ! Receipt.error(
      new Exception("Not a valid component. Try 'dal' or 'service')"), "Error when processing request")

    case msg ⇒ log.warn(s"unable to process message: $msg")
  }
}
