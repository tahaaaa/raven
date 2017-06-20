package com.opentok.raven.resources

import spray.json._
import spray.json.DefaultJsonProtocol._

case class SDK(
  name: String,
  versions: List[String]
)

object SDK {
  implicit val sdkJsonFormat: RootJsonFormat[SDK] = jsonFormat2(SDK.apply)
}
