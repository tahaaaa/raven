package com.opentok.raven.service.actors

import akka.actor.{Actor, ActorLogging}
import akka.event.LoggingAdapter
import com.opentok.raven.GlobalConfig
import com.opentok.raven.model.{Receipt, Template}
import com.sendgrid.SendGrid
import com.sendgrid.SendGrid.Email

import scala.util.Try

class SendgridActor extends Actor with ActorLogging {

  import SendgridActor._

  implicit val logger: LoggingAdapter = log

  val client = new SendGrid(GlobalConfig.SENDGRID_API_KEY)

  override def receive: Receive = {
    case tmp: Template ⇒
      val receipt: Receipt = Try(client.send(tmp)).map { rsp ⇒
        Receipt(rsp.getStatus)
      }.recover {
        case e: Exception ⇒
          Receipt.error(e, "Error when connecting with SendGrid client")
      }.get
      sender() ! receipt
    case anyElse ⇒ log.warning(s"Not an acceptable request $anyElse")
  }
}

object SendgridActor {

  //translates com.opentok.raven.model.Template to com.sendgrid.SendGrid.Email
  //for sendgrid client
  implicit def templateToSendgridEmail(tmp: Template): Email = {
    val m = new Email()
      .setSubject(tmp.subject)
      .setTo(Array(tmp.to))
      .setFrom(tmp.from)
      .setHtml(tmp.html)
    tmp.toName.map(l ⇒ m.setToName(Array(l)))
    tmp.fromName.map(m.setFromName)
    tmp.setReply.map(m.setReplyTo)
    m.setCc(tmp.cc.toArray)
    m.setBcc(tmp.bcc.toArray)
    tmp.attachments.map(a ⇒ m.addAttachment(a._1, a._2))
    tmp.headers.map(h ⇒ m.addHeader(h._1, h._2))
    m
  }

}