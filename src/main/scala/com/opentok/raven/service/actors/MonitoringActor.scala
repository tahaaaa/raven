package com.opentok.raven.service.actors

import akka.actor._
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.pattern.{ask, pipe}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.opentok.raven.GlobalConfig
import com.opentok.raven.model.Receipt
import com.opentok.raven.service.actors.MonitoringActor.{ComponentHealthCheck, InFlightEmailsCheck}
import slick.driver.JdbcProfile
import slick.jdbc.JdbcBackend

import scala.util.Try

object MonitoringActor {

  case class ComponentHealthCheck(component: String)
  case object InFlightEmailsCheck

}

class MonitoringActor(certifiedService: ActorRef, priorityService: ActorRef, db: JdbcBackend#Database, driver: JdbcProfile)
  extends Actor with ActorLogging {

  import com.opentok.raven.GlobalConfig.ACTOR_TIMEOUT
  import context.dispatcher

  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val logger: LoggingAdapter = log
  lazy val selfConnectionFlow: Flow[HttpRequest, HttpResponse, Any] =
    Http(context.system).outgoingConnection(GlobalConfig.HOST, GlobalConfig.PORT)

  override def receive: Receive = {

    case check @ InFlightEmailsCheck ⇒
      val cf = certifiedService.ask(InFlightEmailsCheck).mapTo[Map[String, Int]]
      val pf = priorityService.ask(InFlightEmailsCheck).mapTo[Map[String, Int]]

      (for {
        certifiedEmails ← cf
        priorityEmails ← pf
      } yield certifiedEmails ++ priorityEmails) pipeTo sender()

    case ComponentHealthCheck("api") ⇒
      Source.single(RequestBuilding.Get("/v1/monitoring/health/")).via(selfConnectionFlow).runWith(Sink.head).map { i ⇒
        Receipt.success(Some("OK"), None)
      } recover {
        case e: Exception ⇒ Receipt.error(e, "Unable to establish communication with api")
      } pipeTo sender()

    case ComponentHealthCheck("service") ⇒
      (for {
        idCertified ← certifiedService ? Identify("certified")
        idPriority ← priorityService ? Identify("priority")
      } yield Receipt.success(Some("OK"), None)) recover {
        case e: Exception ⇒ Receipt.error(e, "Unable to establish communication with services")
      } pipeTo sender()

    case ComponentHealthCheck("dal") ⇒ sender() ! Try {
      val conn = db.source.createConnection()
      val r = Receipt(conn.createStatement().execute(conn.nativeSQL(GlobalConfig.DB_CHECK)), Some("OK"))
      conn.close()
      r
    }.recover {
      case e: Exception ⇒ Receipt.error(e, s"Unable to establish communication with db $db using driver $driver")
    }.get

    case ComponentHealthCheck(_) ⇒ sender() ! Receipt.error(
      new Exception("Not a valid component. Try 'dal', 'service' or 'api')"), "Error when processing request")

    case msg ⇒ sender() ! Receipt.error(
      new Exception(s"Unable to understand message $msg"), "Not a valid monitoring request")
  }
}
