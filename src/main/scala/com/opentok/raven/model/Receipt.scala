package com.opentok.raven.model

import akka.event.LoggingAdapter
import spray.json.DefaultJsonProtocol._
import spray.json.{JsObject, RootJsonFormat}

/**
 * Service Task receipt
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
    Receipt.receiptJsonFormat.write(this).asJsObject
  }
}

object Receipt {

  implicit val receiptJsonFormat: RootJsonFormat[Receipt] = jsonFormat4(Receipt.apply)

  def success: Receipt = Receipt(success = true)

  def success(message: Option[String], requestId: Option[String])(implicit log: LoggingAdapter): Receipt = {
    message.map(log.info)
    Receipt(
      success = true,
      requestId = requestId,
      message = message
    )
  }

  def error(e: Throwable, message: String, requestId:Option[String] = None)(implicit log: LoggingAdapter): Receipt = {
    log.error(e, message)
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
      message = Some(a.message.getOrElse("") + "-" + b.message.getOrElse("")),
      errors = a.errors ::: b.errors
    )
  }

}
