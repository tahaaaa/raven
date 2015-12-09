package com.opentok.raven.model

import com.opentok.raven.Implicits._
import com.opentok.raven.model.Email._
import spray.json.{DefaultJsonProtocol, JsObject, JsValue, RootJsonFormat}

import scala.util.Try

case class Email(
  id: Option[String],
  subject: String,
  recipients: List[EmailAddress],
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

  type HTML = String
  type EmailAddress = String
  type Injections = JsObject

  import DefaultJsonProtocol._

  implicit val emailJsonFormat: RootJsonFormat[Email] = jsonFormat14(Email.apply)

  //convenience template constructor that uses html.wrap_email_v1
  def wrapTemplate(requestId: Option[String], subject: String, recipient: String,
                   from: String, template: play.twirl.api.Html, fromTemplateId: String,
                   toName: Option[EmailAddress] = None,
                   fromName: Option[String] = None,
                   categories: Option[List[String]] = None,
                   setReply: Option[EmailAddress] = None,
                   cc: Option[List[EmailAddress]] = None,
                   bcc: Option[List[EmailAddress]] = None,
                   attachments: Option[List[(String, String)]] = None,
                   headers: Option[Map[String, String]] = None): Email =
    Email(requestId, subject, recipient :: Nil, from, html.wrap_email_v1(recipient, template).body,
      Some(fromTemplateId), toName, fromName, categories, setReply, cc, bcc, attachments, headers)

  //decoupled from build to check at runtime what templates are available
  def buildPF(requestId: Option[String], recipient: String,
              fields: Map[String, JsValue]): PartialFunction[String, Email] = {

    case templateId @ "confirmation_instructions" ⇒
      wrapTemplate(requestId, "Confirmation Instructions", recipient, "messages@tokbox.com",
        html.confirmation_instructions(fields %> "confirmation_url"),
        templateId, fromName = Some("TokBox"))

    case templateId @ "repeated_email_attempt" ⇒
      wrapTemplate(requestId, "Repeated Email Attempt", recipient, "messages@tokbox.com",
        html.repeated_email_attempt(fields %> "reset_password_link"),
        templateId, fromName = Some("TokBox"))

    case templateId @ "reset_password_instructions" ⇒
      wrapTemplate(requestId, "Reset Password Instructions", recipient, "messages@tokbox.com",
        html.reset_password_instructions(fields %> "reset_password_link"),
        templateId, fromName = Some("TokBox"))

    case templateId @ "developer_invitation" ⇒
      wrapTemplate(requestId, "Developer Invitation", recipient, "messages@tokbox.com",
        html.developer_invitation(fields %> "account_name", fields %> "invitation_link"),
        templateId, fromName = Some("TokBox"))

    case templateId @ "usage_etl_report" ⇒
      val subject = "[Hubble] - Usage ETL Report"
      Email(requestId, subject, recipient :: Nil, "analytics@tokbox.com",
        html.wrap_internal_v1(subject, html.usage_etl_report(
          Try(fields("streamError").convertTo[String]).toOption,
          Try(fields("streamStackTrace").convertTo[String]).toOption,
          fields("hadrosaurErrors").convertTo[List[String]],
          fields("processingErrors").convertTo[List[String]],
          fields("hadrosaurSuccesses").convertTo[Int],
          fields("processingSuccesses").convertTo[Int]
        )).body, Some(templateId), fromName = Some("Hubble"))

  }

  def build(requestId: Option[String], templateId: String, injections: Injections, recipient: String): Try[Email] = Try {
    val fields = injections.fields
    buildPF(requestId, recipient, fields)(templateId)
  }
}
