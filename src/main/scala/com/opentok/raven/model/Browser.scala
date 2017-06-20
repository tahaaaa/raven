package com.opentok.raven.resources

import spray.json._
import spray.json.DefaultJsonProtocol._

case class Browser(
  name: String,
  versions: List[String],
  icon: String
)

object Browser {
  implicit val browserJsonFormat: RootJsonFormat[Browser] = jsonFormat3(Browser.apply)
}
