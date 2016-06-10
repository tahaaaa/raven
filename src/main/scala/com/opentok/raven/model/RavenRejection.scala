package com.opentok.raven.model

import spray.json.JsValue

//marker trait for validation rejections
sealed trait RavenRejection

class InvalidTemplate(template_id: String, cause: Throwable)
  extends Exception(s"invalid template id '$template_id'", cause) with RavenRejection

class MissingInjections(injects: Map[String, JsValue], cause: Throwable)
  extends Exception(s"missing inject in $injects", cause) with RavenRejection

class InvalidInjection(value: String, msg: String)
  extends Exception(s"invalid value '$value': $msg") with RavenRejection

