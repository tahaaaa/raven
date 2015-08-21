package com.opentok.raven.http

import akka.http.scaladsl.marshallers.sprayjson._
import akka.http.scaladsl.marshalling.{Marshaller, ToEntityMarshaller}
import akka.http.scaladsl.model.ContentType
import akka.http.scaladsl.model.MediaTypes._
import com.opentok.raven.model.{Email, Requestable, EmailRequest}
import play.twirl.api.Html
import spray.json.{JsArray, JsValue, RootJsonFormat}

trait JsonProtocols extends SprayJsonSupport {

  implicit object RequestableJsonFormat
    extends RootJsonFormat[Either[List[Requestable], Requestable]] {
    override def write(obj: Either[List[Requestable], Requestable]): JsValue =
      obj match {
        case Right(e: EmailRequest) ⇒ EmailRequest.requestJsonFormat.write(e)
        case Right(e: Email) ⇒ Email.emailJsonFormat.write(e)
        case Left(lReq) if lReq.isEmpty ⇒ JsArray(Vector.empty)
        case Left(lReq) ⇒ JsArray(lReq.map {
          case e: EmailRequest ⇒ EmailRequest.requestJsonFormat.write(e)
          case e: Email ⇒ Email.emailJsonFormat.write(e)
        }.toSeq: _*)
      }

    override def read(json: JsValue): Either[List[Requestable], Requestable] =
      json match {
        case JsArray(lReq) if lReq.isEmpty ⇒ Left(List.empty[Requestable])
        case JsArray(lReq) if lReq.head.asJsObject.fields.exists(_._1.toLowerCase == "template_id") ⇒ Left(lReq.map(EmailRequest.requestJsonFormat.read).toList)
        case JsArray(lReq) ⇒ Left(lReq.map(Email.emailJsonFormat.read).toList)
        case obj: JsValue if obj.asJsObject.fields.exists(_._1.toLowerCase == "template_id") ⇒ Right(EmailRequest.requestJsonFormat.read(obj))
        case obj: JsValue ⇒ Right(Email.emailJsonFormat.read(obj))
      }
  }


  protected def twirlMarshaller[A <: AnyRef : Manifest](contentType: ContentType): ToEntityMarshaller[A] =
    Marshaller.StringMarshaller.wrap(contentType)(_.toString)

  implicit val twirlHtmlMarshaller = twirlMarshaller[Html](`text/html`)

}
