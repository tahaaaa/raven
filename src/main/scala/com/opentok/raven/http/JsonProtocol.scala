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
    extends RootJsonFormat[Either[List[Requestable], Requestable]] {
    override def write(obj: Either[List[Requestable], Requestable]): JsValue =
      obj match {
        case Right(e: EmailRequest) ⇒ requestJsonFormat.write(e)
        case Right(e: Email) ⇒ emailJsonFormat.write(e)
        case Left(lReq) if lReq.isEmpty ⇒ JsArray(Vector.empty)
        case Left(lReq) ⇒ JsArray(lReq.map {
          case e: EmailRequest ⇒ requestJsonFormat.write(e)
          case e: Email ⇒ emailJsonFormat.write(e)
        }.toSeq: _*)
      }

    override def read(json: JsValue): Either[List[Requestable], Requestable] =
      json match {
        case JsArray(lReq) if lReq.isEmpty ⇒ Left(List.empty[Requestable])
        case JsArray(lReq) if lReq.head.asJsObject.fields.exists(_._1.toLowerCase == "template_id") ⇒ Left(lReq.map(requestJsonFormat.read).toList)
        case JsArray(lReq) ⇒ Left(lReq.map(emailJsonFormat.read).toList)
        case obj: JsValue if obj.asJsObject.fields.exists(_._1.toLowerCase == "template_id") ⇒ Right(requestJsonFormat.read(obj))
        case obj: JsValue ⇒ Right(emailJsonFormat.read(obj))
      }
  }


  protected def twirlMarshaller[A <: AnyRef : Manifest](contentType: MediaType): ToEntityMarshaller[A] =
    Marshaller.StringMarshaller.wrap(contentType)(_.toString)

  implicit val twirlHtmlMarshaller = twirlMarshaller[Html](MediaTypes.`text/html`)

}

object JsonProtocol extends JsonProtocol
