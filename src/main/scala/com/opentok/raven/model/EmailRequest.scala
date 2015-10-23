package com.opentok.raven.model

import java.util.UUID

import spray.json._


/**
 * Service email task request
 * @param to Email address of the recipient
 * @param template_id template name in resources/templates without extension
 * @param status status of the request. Check sealed trait [[com.opentok.raven.model.EmailRequest.Status]]
 * @param inject map of key value pairs to inject to the template
 */
case class EmailRequest(
  to: String,
  template_id: String,
  inject: Option[JsObject],
  status: Option[EmailRequest.Status],
  id: Option[String]
) extends Requestable {

  def validated: EmailRequest = {
    require(Email.buildPF(None, "", Map.empty).isDefinedAt(template_id),
      s"Template not found with template_id $template_id")
    this
  }

  @transient
  lazy val json: JsObject = {
    EmailRequest.requestJsonFormat.write(this).asJsObject
  }
}

object EmailRequest {
  import spray.json.DefaultJsonProtocol._

  //transforms an incoming request without id and status
  val fillInRequest = { req: EmailRequest ⇒
    req.copy(
      id = Some(UUID.randomUUID.toString),
      status = Some(EmailRequest.Pending)
    )
  }

  sealed trait Status

  case object Pending extends Status

  case object Succeeded extends Status

  case object Failed extends Status


  implicit object EmailRequestStatusFormat extends RootJsonFormat[EmailRequest.Status] {
    def write(obj: EmailRequest.Status) = JsString(obj.toString)

    def read(json: JsValue): EmailRequest.Status = json match {
      case JsString("Pending") ⇒ Pending
      case JsString("Succeeded") ⇒ Succeeded
      case JsString("Failed") ⇒ Failed
      case s ⇒ throw new SerializationException(s"Unrecognized EmailReceipt.Status '$s'")
    }
  }
  implicit val requestJsonFormat: RootJsonFormat[EmailRequest] = jsonFormat5(EmailRequest.apply)

}
