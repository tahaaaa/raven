package com.opentok.raven

import spray.json._

object Implicits {

  //helper methods to extract values out of the fields to be injected in the template
  implicit class pimpedFields(fields: Map[String, JsValue]) {

    //extract string value
    def %>(value: String)(implicit j: JsonFormat[String]): String = extract[String](value)

    def ?>(value: String)(implicit j: JsonFormat[String]): Option[String] = fields.get(value).map(_.convertTo[String])

    def extract[T: JsonFormat](value: String): T = fields(value).convertTo[T]

  }
}
