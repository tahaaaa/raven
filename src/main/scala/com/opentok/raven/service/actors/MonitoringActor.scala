package com.opentok.raven.service.actors

import akka.actor._
import akka.event.LoggingAdapter
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import com.opentok.raven.model.Receipt
import com.opentok.raven.service.actors.MonitoringActor.{ComponentHealthCheck, PendingEmailsCheck}
import com.typesafe.config.ConfigFactory
import slick.driver.JdbcProfile
import slick.jdbc.JdbcBackend

import scala.util.Try

object MonitoringActor {

  case class ComponentHealthCheck(component: String)
  case object PendingEmailsCheck

}

class MonitoringActor(certifiedService: ActorRef, priorityService: ActorRef,
                      db: JdbcBackend#Database, driver: JdbcProfile, dbCheck: String, t: Timeout)
  extends Actor with ActorLogging {

  import context.dispatcher

  implicit val logger: LoggingAdapter = log
  implicit val timeout: Timeout = t
  val config = ConfigFactory.load()
  val dbHost= config.getString("raven.database.properties.serverName")
  val dbPort= config.getInt("raven.database.properties.portNumber")

  override def receive: Receive = {

    case check @ PendingEmailsCheck ⇒
      val cf = certifiedService.ask(PendingEmailsCheck).mapTo[Map[String, Int]]
      val pf = priorityService.ask(PendingEmailsCheck).mapTo[Map[String, Int]]

      (for {
        certifiedEmails ← cf
        priorityEmails ← pf
      } yield certifiedEmails ++ priorityEmails) pipeTo sender()

    case ComponentHealthCheck("service") ⇒
      (for {
        idCertified ← certifiedService ? Identify("certified")
        idPriority ← priorityService ? Identify("priority")
      } yield Receipt.success(Some("OK"), None)) recover {
        case e: Exception ⇒ Receipt.error(e, "Unable to establish communication with services")
      } pipeTo sender()

    case ComponentHealthCheck("dal") ⇒ sender() ! Try {
      val conn = db.source.createConnection()
      val r = Receipt(conn.createStatement().execute(conn.nativeSQL(dbCheck)),
        message = Some("Ok"))
      conn.close()
      r
    }.recover {
      case e: Exception ⇒
        Receipt.error(e, "Unable to establish communication with " +
          s"db $dbHost:$dbPort using driver $driver")
    }.get

    case ComponentHealthCheck(_) ⇒ sender() ! Receipt.error(
      new Exception("Not a valid component. Try 'dal' or 'service')"), "Error when processing request")

    case msg ⇒ sender() ! Receipt.error(
      new Exception(s"Unable to understand message $msg"), "Not a valid monitoring request")
  }
}
