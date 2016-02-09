package com.opentok.raven

import java.util.concurrent.TimeUnit

import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}
import slick.jdbc.JdbcBackend.Database

trait RavenConfig {
  val HOST: String
  val PORT: Int
  val SENDGRID_API_KEY: String
  val MAX_RETRIES: Int
  val DEFERRER: Int
  val CERTIFIED_POOL: Int
  val PRIORITY_POOL: Int
  val ACTOR_TIMEOUT: Timeout
  val ACTOR_INNER_TIMEOUT: Timeout
  val ENDPOINT_TIMEOUT: Timeout
  val DB_CHECK: String
}

abstract class FromResourcesConfig(config: Config) extends RavenConfig {

  val HOST = config.getString("raven.host")

  val PORT = config.getInt("raven.port")

  val SENDGRID_API_KEY = config.getString("raven.sendgrid.api-key")

  val MAX_RETRIES = config.getInt("raven.max-retries")

  val DEFERRER = config.getInt("raven.deferrer")

  val CERTIFIED_POOL = config.getInt("raven.certified-pool")

  val PRIORITY_POOL = config.getInt("raven.priority-pool")

  implicit val ACTOR_TIMEOUT: Timeout = {
    val dur = config.getDuration("raven.actor-timeout")
    Timeout(dur.toMillis, TimeUnit.MILLISECONDS)
  }

  implicit val ACTOR_INNER_TIMEOUT: Timeout = ACTOR_TIMEOUT.duration * 2

  implicit val ENDPOINT_TIMEOUT: Timeout = {
    val dur = config.getDuration("raven.endpoint-timeout")
    Timeout(dur.toMillis, TimeUnit.MILLISECONDS)
  }

  implicit val DB_CHECK: String = config.getString("raven.database.connectionTestQuery")

}
