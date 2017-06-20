package com.opentok.raven.resources

import com.opentok.raven.model.InvalidInjection

import spray.json._
import spray.json.DefaultJsonProtocol._

case class Update(
  text: String,
  link: String,
  linkText: String
)

object Update {
  implicit val updateJsonFormat: RootJsonFormat[Update] = jsonFormat3(Update.apply)
}
