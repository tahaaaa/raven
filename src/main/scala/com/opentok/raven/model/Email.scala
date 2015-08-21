package com.opentok.raven.model

import java.util.UUID

import com.opentok.raven.model.Email._
import spray.json.{DefaultJsonProtocol, JsObject, RootJsonFormat}

import scala.util.Try

case class Email(
  id: Option[String],
  subject: String,
  to: EmailAddress,
  from: EmailAddress,
  html: HTML,
  fromTemplateId: Option[String] = None,
  toName: Option[EmailAddress] = None,
  fromName: Option[String] = None,
  categories: Option[List[String]] = None,
  setReply: Option[EmailAddress] = None,
  cc: Option[List[EmailAddress]] = None,
  bcc: Option[List[EmailAddress]] = None,
  attachments: Option[List[(String, String)]] = None,
  headers: Option[Map[String, String]] = None
) extends Requestable

object Email {
  import DefaultJsonProtocol._

  implicit val emailJsonFormat: RootJsonFormat[Email] = jsonFormat14(Email.apply)

  def fillInEmail(e: Email):Email = e.copy(id = Some(UUID.randomUUID.toString))

  type HTML = String
  type EmailAddress = String
  type Injections = JsObject

  def build(requestId: Option[String], templateId: String, injections: Injections, recipient: String): Try[Email] = Try {
    val fields = injections.fields
    templateId match {
      case templateId @ "twirl_test" ⇒
        Email(requestId, "Test email", recipient, "ba@tokbox.com",
          html.twirl_test(fields("a").convertTo[String], fields("b").convertTo[Int]).body,
          fromName = Some("Business Analytics"), fromTemplateId = Some(templateId), setReply = Some("no-reply@tokbox.com"))
    }
  }
}