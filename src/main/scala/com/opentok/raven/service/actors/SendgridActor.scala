package com.opentok.raven.service.actors

import akka.actor.{Actor, ActorLogging}
import akka.event.LoggingAdapter
import com.opentok.raven.model.{Email, Receipt}
import com.sendgrid.SendGrid

import scala.util.Try

/**
 * Processes [[com.opentok.raven.model.Email]] and sends it via SendGrid SMTPAPI
 * @param apiKey to authenticate client
 */
class SendgridActor(apiKey: String) extends Actor with ActorLogging {

  import SendgridActor._

  implicit val logger: LoggingAdapter = log

  val client = new SendGrid(apiKey)

  val errorMsg = "Error when connecting with SendGrid client"

  override def receive: Receive = {
    case tmp: Email ⇒
      log.info(s"Received Email with id ${tmp.id}")
      val receipt: Receipt = Try(client.send(tmp)).map {
        case rsp if rsp.getStatus ⇒ Receipt(rsp.getStatus, requestId = tmp.id)
        case rsp ⇒
          val combined = errorMsg + " " + rsp.getMessage
          log.error(combined)
          Receipt.error(new Exception(combined), errorMsg, tmp.id)
      }.recover {
        case e: Exception ⇒
          log.error(e, errorMsg)
          Receipt.error(e, errorMsg)
      }.get
      sender() ! receipt
    case anyElse ⇒ log.warning(s"Not an acceptable request $anyElse")
  }
}

object SendgridActor {

  /**
   * transforms [[com.opentok.raven.model.Email]] to [[com.sendgrid.SendGrid.Email]]
   */
  implicit def templateToSendgridEmail(tmp: Email): com.sendgrid.SendGrid.Email = {
    val m = new com.sendgrid.SendGrid.Email()
      .setSubject(tmp.subject)
      .setTo(tmp.recipients.toArray)
      .setFrom(tmp.from)
      .setHtml(tmp.html)
    tmp.toName.map(l ⇒ m.setToName(Array(l)))
    tmp.fromName.map(m.setFromName)
    tmp.categories.map(_.map(m.addCategory))
    tmp.setReply.map(m.setReplyTo)
    tmp.cc.map(cc ⇒ m.setCc(cc.toArray))
    tmp.bcc.map(bcc ⇒ m.setBcc(bcc.toArray))
    tmp.attachments.map(o ⇒ o.map(a ⇒ m.addAttachment(a._1, a._2)))
    tmp.headers.map(o ⇒ o.map(h ⇒ m.addHeader(h._1, h._2)))
    m
  }

}