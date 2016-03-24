package com.opentok.raven.model

import com.sendgrid.SendGrid
import org.slf4j.LoggerFactory

import scala.concurrent.{Future, ExecutionContext}
import scala.util.Try
import scala.util.matching.Regex

/**
 * Processes [[com.opentok.raven.model.Email]] and sends it via SendGrid SMTPAPI
 * @param client [[com.sendgrid.SendGrid]] client
 * @param prd if set to false, only emails with recipients matching `restrictTo` will be processed
 * @param restrictTo regex to match recipients against
 */
class SendgridProvider(client: SendGrid,
                       prd: Boolean,
                       restrictTo: Option[String]) extends Provider {
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

  val log = LoggerFactory.getLogger(this.getClass)

  val errorMsg = "error when connecting with SendGrid client"

  val restrictRgxMaybe: Option[Regex] = restrictTo.map(new Regex(_))

  def doSend(em: Email)(implicit ctx: ExecutionContext): Future[Receipt] = Future {
    log.debug(s"received email with id '{}' addressed to '{}' with subject '{}'", em.id, em.recipients, em.subject)
    Try(client.send(em)).map {
      case rsp if rsp.getStatus ⇒
        log.debug(s"successfully sent email with id '{}", em.id)
        Receipt(rsp.getStatus, requestId = em.id)
      case rsp ⇒
        val combined = errorMsg + " " + rsp.getMessage
        log.error(combined)
        Receipt.error(new Exception(combined), errorMsg, em.id)
    }.recover {
      case e: Exception ⇒
        log.error(errorMsg, e)
        Receipt.error(e, errorMsg)
    }.get
  }

  override def send(em: Email)(implicit ctx: ExecutionContext): Future[Receipt] = {
    //if not prd filter out recipients that don't match regex restrictTo
    if (!prd) {
      val restrictRgx = restrictRgxMaybe.get //guaranteed by RavenConfig when booting up
      log.warn(s"flag 'prd' set to false: filtering recipients that don't match regex '$restrictTo'")
      val finalRecipients = em.recipients.filter(restrictRgx.pattern.matcher(_).matches)

      if (finalRecipients.isEmpty) {
        val msg = s"email '${em.id.get}' not sent: flag prd set to false and no recipients matched regex: '${restrictRgx.regex}'"
        log.warn(msg)
        Future.successful(Receipt(
          success = true,
          errors = List(msg),
          requestId = em.id))
      } else doSend(em.copy(recipients = finalRecipients))
    } else doSend(em)
  }
}
