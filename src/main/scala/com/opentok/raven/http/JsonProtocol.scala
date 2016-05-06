package com.opentok.raven.http

import akka.http.scaladsl.marshallers.sprayjson._
import akka.http.scaladsl.marshalling.{Marshaller, ToEntityMarshaller}
import akka.http.scaladsl.model.{MediaType, MediaTypes}
import com.opentok.raven.model.{Receipt, Email, EmailRequest, Requestable}
import play.twirl.api.Html
import spray.json.{DefaultJsonProtocol, JsArray, JsValue, RootJsonFormat}

trait JsonProtocol extends SprayJsonSupport with DefaultJsonProtocol {

  implicit val requestJsonFormat: RootJsonFormat[EmailRequest] = jsonFormat5(EmailRequest.apply)

  implicit val emailJsonFormat: RootJsonFormat[Email] = jsonFormat14(Email.apply)

  implicit val receiptJsonFormat: RootJsonFormat[Receipt] = jsonFormat4(Receipt.apply)

  implicit object RequestableJsonFormat
    extends RootJsonFormat[Requestable] {

    override def write(obj: Requestable): JsValue =
      obj match {
        case e: EmailRequest ⇒ requestJsonFormat.write(e)
        case e: Email ⇒ emailJsonFormat.write(e)
      }

    override def read(json: JsValue): Requestable =
      json match {
        case obj: JsValue if obj.asJsObject.fields.exists(_._1.toLowerCase == "template_id") ⇒ requestJsonFormat.read(obj)
        case obj: JsValue ⇒ emailJsonFormat.read(obj)
      }
  }


  protected def twirlMarshaller[A <: AnyRef : Manifest](contentType: MediaType): ToEntityMarshaller[A] =
    Marshaller.StringMarshaller.wrap(contentType)(_.toString)

  implicit val twirlHtmlMarshaller = twirlMarshaller[Html](MediaTypes.`text/html`)

}

object JsonProtocol extends JsonProtocol
