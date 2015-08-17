package com.opentok.raven.model

import akka.event.LoggingAdapter
import spray.json.DefaultJsonProtocol._
import spray.json.{JsObject, RootJsonFormat}

/**
 * Task receipt.
 * @param success Boolean if whether task was successful or not
 * @param errors List of errors, if any.
 */
case class Receipt(success: Boolean, message: String = "", errors: List[String] = List.empty) {

  @transient
  lazy val json: JsObject = {
    Receipt.receiptJsonFormat.write(this).asJsObject
  }
}

object Receipt {

  implicit val receiptJsonFormat: RootJsonFormat[Receipt] = jsonFormat3(Receipt.apply)

  def success: Receipt = Receipt(success = true)

  def success(msg: String)(implicit log: LoggingAdapter): Receipt = {
    log.info(msg)
    Receipt(success = true, message = msg)
  }

  def error(e: Throwable, message: String = "")(implicit log: LoggingAdapter): Receipt = {
    log.error(e, message)
    Receipt(success = false, message = message, errors = List(e.getMessage))
  }

  def reduce(s: Seq[Receipt]): Receipt = s.reduce { (a, b) ⇒
    Receipt(a.success && b.success, a.message + "-" + b.message, a.errors ::: b.errors)
  }

}
