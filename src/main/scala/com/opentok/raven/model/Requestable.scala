package com.opentok.raven.model

/**
 * Common Incoming Request trait
 */
trait Requestable {
  val id: Option[String]
}
