package com.opentok.raven.model

case class SendEmailRequest(
  to: Seq[String],
  inject: Map[String, Seq[String]],
  template: String,
  categories: Seq[String] = Seq.empty[String]
) {

}
