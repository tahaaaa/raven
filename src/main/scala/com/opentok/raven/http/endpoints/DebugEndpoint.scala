package com.opentok.raven.http.endpoints

import java.io.File
import java.net.URI
import java.nio.file.{FileSystems, Files}
import java.util.Collections

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.{ContentType, HttpCharsets, HttpEntity, MediaTypes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import akka.util.CompactByteString
import com.opentok.raven.model.Email
import spray.json._

import scala.collection.JavaConversions._
import scala.util.Try

class DebugEndpoint(implicit val mat: Materializer, system: ActorSystem) {

  import com.opentok.raven.http.JsonProtocol._

  implicit val logger: LoggingAdapter = system.log

  val classLoader = this.getClass.getClassLoader

  //builds a spray.json.JsObject (aliased to Email.Injections)
  def toInjections(params: Map[String, String]): Email.Injections = {
    params.foldLeft(Map.empty[String, JsValue]) {
      case (m, (k, v)) ⇒
        //try to parse numeric
        val parsed = Try(v.toDouble).map(_.toJson).getOrElse(v.toJson)
        m.updated(k, parsed)
    }
  }

  val route: Route = get {
    pathPrefix("debug") {
      path("template" / Segment) {
        case templateId if Email.buildPF(None, "", Map.empty).isDefinedAt(templateId) ⇒ parameterMap {
          params ⇒ Try {
            HttpEntity.Strict(
              ContentType(MediaTypes.`text/html`, HttpCharsets.`UTF-8`), CompactByteString(
                Email.build(None, templateId, toInjections(params), "").get.html
              )
            )
          }.map(em ⇒ complete(em)).recover { case e ⇒ reject }.get
        }
        case notAvailable ⇒ reject
      } ~
        path("template" / Segment) {
          case id if id.endsWith(".scala.html") ⇒ getFromResource("templates/" + id)
          case id ⇒ getFromResource("templates/" + id + ".scala.html")
        } ~
        path("template") {
          pathEndOrSingleSlash {
            val uri: URI = classLoader.getResource("templates").toURI
            val files: List[File] = uri.getScheme match {
              case "jar" ⇒
                val fs = FileSystems.newFileSystem(uri, Collections.emptyMap[String, Any]())
                val files = Files.walk(fs.getPath("templates"), 1).iterator().toList.map(p ⇒ new File(p.toString))
                fs.close()
                files
              case _ ⇒ new File(uri).listFiles.toList
            }
            val available = files.map(_.getName.replaceAllLiterally(".scala.html", ""))
              .filter(Email.buildPF(None, "", Map.empty).isDefinedAt)
            complete(available)
          }
        }
    }
  }
}
