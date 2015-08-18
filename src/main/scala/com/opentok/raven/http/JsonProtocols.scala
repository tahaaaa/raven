package com.opentok.raven.http

import akka.http.scaladsl.marshallers.sprayjson._
import akka.http.scaladsl.marshalling.{Marshaller, ToEntityMarshaller}
import akka.http.scaladsl.model.ContentType
import akka.http.scaladsl.model.MediaTypes._
import com.opentok.raven.model.EmailRequest
import play.twirl.api.Html
import spray.json.{JsArray, JsValue, RootJsonFormat}

trait JsonProtocols extends SprayJsonSupport {

  implicit object EmailRequestJsonFormat
    extends RootJsonFormat[Either[List[EmailRequest], EmailRequest]] {
    override def write(obj: Either[List[EmailRequest], EmailRequest]): JsValue =
      obj match {
        case Right(req) ⇒ EmailRequest.requestJsonFormat.write(req)
        case Left(lReq) ⇒ JsArray(lReq.map(EmailRequest.requestJsonFormat.write).toSeq: _*)
      }

    override def read(json: JsValue): Either[List[EmailRequest], EmailRequest] =
      json match {
        case JsArray(lReq) ⇒ Left(lReq.map(EmailRequest.requestJsonFormat.read).toList)
        case obj: JsValue ⇒ Right(EmailRequest.requestJsonFormat.read(obj))
      }
  }


  protected def twirlMarshaller[A <: AnyRef : Manifest](contentType: ContentType): ToEntityMarshaller[A] =
    Marshaller.StringMarshaller.wrap(contentType)(_.toString)

  implicit val twirlHtmlMarshaller = twirlMarshaller[Html](`text/html`)

}
