package com.opentok.raven.resources

import spray.json.DefaultJsonProtocol._
import spray.json._

case class Insights(
  rate: Float,
  delta: String
)

object Insights {
  implicit val insightsJsonFormat: RootJsonFormat[Insights] = jsonFormat2(Insights.apply)
}
