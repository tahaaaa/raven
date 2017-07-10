package com.opentok.raven.resources

import spray.json.DefaultJsonProtocol._
import spray.json._

case class Update(
  text: String,
  link: String,
  linkText: String
)

object Update {
  implicit val updateJsonFormat: RootJsonFormat[Update] = jsonFormat3(Update.apply)
}
