package com.opentok.raven.service.actors

import akka.actor.{Actor, ActorLogging}
import akka.event.LoggingAdapter
import akka.http.scaladsl.{HttpsContext, Http}
import akka.http.scaladsl.model.{HttpResponse, HttpRequest}
import akka.pattern._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import com.opentok.raven.GlobalConfig
import com.opentok.raven.model.EmailRequest
import com.sendgrid.SendGrid
import com.sendgrid.SendGrid.Email


class SendgridActor extends Actor with ActorLogging {

  import context.dispatcher

  implicit val logger: LoggingAdapter = log
//  implicit val materializer: ActorMaterializer = ActorMaterializer()
//
//  lazy val sendgridConnectionFlow: Flow[HttpRequest, HttpResponse, Any] =
//    Http(context.system).outgoingConnectionTls(GlobalConfig.SENDGRID_API_URL, GlobalConfig.PORT)

  val client = new SendGrid(GlobalConfig.SENDGRID_API_KEY)

  override def receive: Receive = {
    case req: EmailRequest ⇒
      val email = new Email()
      email.addTo("ernest@tokbox.com")
      email.setFrom("ba@tokbox.com")
      email.setFromName("Business Analytics")
      email.setSubject("Hello World")
      email.setHtml("<h1>Hello</h1>")
      client.send(email)
  }
}
