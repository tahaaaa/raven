package com.opentok.raven.resources

import spray.json.DefaultJsonProtocol._
import spray.json._

case class Project(
  name: String,
  expiredTokens: Int
)

object Project {
  implicit val projectJsonFormat: RootJsonFormat[Project] = jsonFormat2(Project.apply)
}
