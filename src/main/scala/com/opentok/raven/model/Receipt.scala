package com.opentok.raven.model

import com.opentok.raven.http.JsonProtocol
import spray.json.JsObject

/**
 * Service Task receipt. Used for basic communication between actors
 * and from the actor system to the outside world.
 *
 * @param success Boolean if whether task was successful or not
 * @param requestId Optional request id value
 * @param message Optional reply message
 * @param errors List of errors, if any.
 */
case class Receipt(
  success: Boolean,
  requestId: Option[String] = None,
  message: Option[String] = None,
  errors: List[String] = List.empty) {

  @transient
  lazy val json: JsObject = {
    JsonProtocol.receiptJsonFormat.write(this).asJsObject
  }
}

object Receipt {

  def success: Receipt = Receipt(success = true)

  def success(message: Option[String], requestId: Option[String]): Receipt = {
    Receipt(
      success = true,
      requestId = requestId,
      message = message
    )
  }

  def error(e: Throwable, message: String, requestId:Option[String] = None): Receipt = {
    Receipt(
      success = false,
      requestId = requestId,
      message = Some(message),
      errors = List(e.getMessage)
    )
  }

  def reduce(s: Seq[Receipt]): Receipt = s.reduce { (a, b) ⇒
    Receipt(
      success = a.success && b.success,
      message = Some(a.message.getOrElse("") + "; " + b.message.getOrElse("")),
      errors = a.errors ::: b.errors
    )
  }

}
