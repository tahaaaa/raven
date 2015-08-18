package com.opentok.raven.model

import spray.json.DefaultJsonProtocol._
import spray.json.{JsObject, RootJsonFormat}
import java.util.UUID


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
  inject: Map[String, String] = Map.empty,
  status: EmailRequest.Status = EmailRequest.Pending,
  id: String = UUID.randomUUID()
) {

  @transient
  lazy val json: JsObject = {
    EmailRequest.requestJsonFormat.write(this).asJsObject
  }

}

object EmailRequest {


  sealed trait Status

  case object Pending

  case object Processed


  implicit val requestJsonFormat: RootJsonFormat[EmailRequest] = jsonFormat5(EmailRequest.apply)

}
