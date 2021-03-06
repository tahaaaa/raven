package com.opentok.raven

import java.util.concurrent.TimeUnit

import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}
import slick.jdbc.JdbcBackend.Database

import scala.util.Try

trait RavenConfig {
  val PRD: Boolean
  val RESTRICT_TO: Option[String]
  val HOST: String
  val PORT: Int
  val SENDGRID_API_KEY: String
  val MAX_RETRIES: Int
  val DEFERRER: Int
  val CERTIFIED_POOL: Int
  val PRIORITY_POOL: Int
  val ACTOR_TIMEOUT: Timeout
  val ENDPOINT_TIMEOUT: Timeout
  val DB_CHECK: String
}

abstract class FromResourcesConfig(config: Config) extends RavenConfig {

  val PRD: Boolean = config.getBoolean("raven.prd")

  val RESTRICT_TO = Try(config.getString("raven.restrict-to")).toOption

  if (!PRD) {
    assert(RESTRICT_TO.isDefined, "'restrict-to' must be set in config if 'prd' flag is set to false!")
  }

  val HOST = config.getString("raven.host")

  val PORT = config.getInt("raven.port")

  val SENDGRID_API_KEY = config.getString("raven.sendgrid.api-key")

  val MAX_RETRIES = config.getInt("raven.max-retries")

  val DEFERRER = config.getInt("raven.deferrer")

  assert(DEFERRER != 0, "deferrer cannot be 0!")

  val CERTIFIED_POOL = config.getInt("raven.certified-pool")

  val PRIORITY_POOL = config.getInt("raven.priority-pool")

  implicit val ACTOR_TIMEOUT: Timeout = {
    val dur = config.getDuration("raven.actor-timeout")
    Timeout(dur.toMillis, TimeUnit.MILLISECONDS)
  }

  implicit val ENDPOINT_TIMEOUT: Timeout = {
    val dur = config.getDuration("raven.endpoint-timeout")
    Timeout(dur.toMillis, TimeUnit.MILLISECONDS)
  }

  assert(ACTOR_TIMEOUT.duration * DEFERRER * MAX_RETRIES < ENDPOINT_TIMEOUT.duration,
    s"max-retries($MAX_RETRIES) * deferrer($DEFERRER) * actor-timeout($ACTOR_TIMEOUT) " +
      s"should be smaller than endpoint timeout($ENDPOINT_TIMEOUT)")

  implicit val DB_CHECK: String = config.getString("raven.database.connectionTestQuery")

}
