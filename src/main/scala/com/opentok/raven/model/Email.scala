package com.opentok.raven.model

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

  //decoupled from build to check at runtime what templates are available
  def buildPF(requestId: Option[String], recipients: List[String], fields: Map[String, JsValue]): PartialFunction[String, Email] = {
    case templateId @ "twirl_test" ⇒
      Email(requestId, "Test email", recipients, "ba@tokbox.com",
        html.twirl_test(fields("a").convertTo[String], fields("b").convertTo[String].toInt).body,
        fromName = Some("Business Analytics"), fromTemplateId = Some(templateId), setReply = Some("no-reply@tokbox.com"))
  }

  def build(requestId: Option[String], templateId: String, injections: Injections, recipients: List[String]): Try[Email] = Try {
    val fields = injections.fields
    buildPF(requestId, recipients, fields)(templateId)
  }
}
