package com.opentok.raven.http

import akka.http.scaladsl.marshallers.sprayjson._
import akka.http.scaladsl.marshalling.{Marshaller, ToEntityMarshaller}
import akka.http.scaladsl.model.ContentType
import akka.http.scaladsl.model.MediaTypes._
import com.opentok.raven.model.Receipt
import com.opentok.raven.service.actors.EmailSupervisor.RelayEmailCmd
import play.twirl.api.{Html, Txt, Xml}
import spray.json._

trait JsonProtocols extends SprayJsonSupport {

  import DefaultJsonProtocol._

  implicit val rootJsonReaderRelayEmailCmd: RootJsonFormat[RelayEmailCmd] = jsonFormat1(RelayEmailCmd.apply)

  implicit val rootJsonReaderReceipt: RootJsonFormat[Receipt] = Receipt.receiptJsonFormat

  protected def twirlMarshaller[A <: AnyRef: Manifest](contentType: ContentType): ToEntityMarshaller[A] =
    Marshaller.StringMarshaller.wrap(contentType)(_.toString)

  implicit val twirlHtmlMarshaller = twirlMarshaller[Html](`text/html`)

}
