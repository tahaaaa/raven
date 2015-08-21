package com.opentok.raven.http.endpoints

import java.io.File

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.{HttpEntity, ContentType}
import akka.http.scaladsl.model.MediaTypes._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import akka.util.CompactByteString
import com.opentok.raven.http.Endpoint
import com.opentok.raven.model.Email
import spray.json._

import scala.util.{Success, Failure}

class DebugEndpoint(implicit val mat: Materializer, system: ActorSystem) extends Endpoint with DefaultJsonProtocol {
  implicit val logger: LoggingAdapter = system.log

  val classLoader = this.getClass.getClassLoader

  override val route: Route = get {
    pathPrefix("debug") {
      path("template" / Segment) {
        case templateId if Email.buildPF(None, "", Map.empty).isDefinedAt(templateId) ⇒ parameterMap {
          case params if params.nonEmpty ⇒ complete {
            HttpEntity.Strict(ContentType(`text/html`), CompactByteString(Email.build(None, templateId, params.toJson.asJsObject, "") match {
              case Success(email) ⇒ email.html
              case Failure(e) ⇒ "There was an error when building template: " + e
            }))
          }
          case _ ⇒ reject
        }
        case notAvailable ⇒ reject
      } ~
        path("template" / Segment) {
          case id if id.endsWith(".scala.html") ⇒ getFromResource("templates/" + id)
          case id ⇒ getFromResource("templates/" + id + ".scala.html")
        } ~
        path("template") {
          pathEndOrSingleSlash {
            val files = new File(classLoader.getResource("templates").toURI).listFiles().map(_.getName.replaceAllLiterally(".scala.html", ""))
            val available = files.filter(Email.buildPF(None, "", Map.empty).isDefinedAt)
            complete(available)
          }
        }
    }
  }
}
