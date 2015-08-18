package com.opentok.raven.http

import akka.http.scaladsl.marshallers.sprayjson._
import akka.http.scaladsl.marshalling.{Marshaller, ToEntityMarshaller}
import akka.http.scaladsl.model.ContentType
import akka.http.scaladsl.model.MediaTypes._
import com.opentok.raven.model.{EmailRequest, Receipt}
import play.twirl.api.Html
import spray.json._

trait JsonProtocols extends SprayJsonSupport {

  import DefaultJsonProtocol._

  implicit val rootJsonReaderEmailRequest: RootJsonFormat[EmailRequest] = EmailRequest.requestJsonFormat

  implicit val rootJsonReaderReceipt: RootJsonFormat[Receipt] = Receipt.receiptJsonFormat

  protected def twirlMarshaller[A <: AnyRef: Manifest](contentType: ContentType): ToEntityMarshaller[A] =
    Marshaller.StringMarshaller.wrap(contentType)(_.toString)

  implicit val twirlHtmlMarshaller = twirlMarshaller[Html](`text/html`)

}
