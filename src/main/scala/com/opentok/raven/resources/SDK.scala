package com.opentok.raven.resources

import spray.json.DefaultJsonProtocol._
import spray.json._

case class SDK(
  name: String,
  versions: List[String]
)

object SDK {
  implicit val sdkJsonFormat: RootJsonFormat[SDK] = jsonFormat2(SDK.apply)
}
