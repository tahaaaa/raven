package com.opentok.raven.model

import java.io.File

import com.opentok.raven.model.Template._
import spray.json.{DefaultJsonProtocol, JsObject}

import scala.util.Try

object Template {
  import DefaultJsonProtocol._

  type ID = String
  type HTML = String
  type EmailAddress = String
  type Injections = JsObject

  def build(id: ID, injections: Injections, recipient: String): Try[Template] = Try {
    val fields = injections.fields
    id match {
      case id @ "twirl_test" ⇒
        Template(id, "Test email", recipient, "ba@tokbox.com",
          html.twirl_test(fields("a").convertTo[String], fields("b").convertTo[Int]).body,
          fromName = Some("Business Analytics"), setReply = Some("no-reply@tokbox.com"))
    }
  }
}

case class Template(
  id: ID,
  subject: String,
  to: EmailAddress,
  from: EmailAddress,
  html: HTML,
  toName: Option[EmailAddress] = None,
  fromName: Option[String] = None,
  setReply: Option[EmailAddress] = None,
  cc: List[EmailAddress] = List.empty,
  bcc: List[EmailAddress] = List.empty,
  attachments: List[(String, File)] = List.empty,
  headers: Map[String, String] = Map.empty
)