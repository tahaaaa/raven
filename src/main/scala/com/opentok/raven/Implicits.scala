package com.opentok.raven

import spray.json._

/**
 * Created by ernest on 10/13/15.
 */
object Implicits {

  //helper methods to extract values out of the fields to be injected in the template
  implicit class pimpedFields(fields: Map[String, JsValue]) {

    //extract string value
    def %>(value: String)(implicit j: JsonFormat[String]): String = extract[String](value)

    def extract[T: JsonFormat](value: String): T = fields(value).convertTo[T]

  }

}
