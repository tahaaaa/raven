package com.opentok.raven

import java.util.concurrent.TimeUnit

import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}
import slick.jdbc.JdbcBackend.Database

/**
 * Created by ernest on 7/8/15.
 */
object GlobalConfig extends GlobalConfig(ConfigFactory.load())

class GlobalConfig(config: Config) {

  val HOST = config.getString("raven.host")

  val PORT = config.getInt("raven.port")
  
  val SENDGRID_API_KEY = config.getString("raven.sendgrid.api-key")

  val MAX_RETRIES = config.getInt("raven.max-retries")

  val CERTIFIED_POOL = config.getInt("raven.certified-pool")

  val PRIORITY_POOL = config.getInt("raven.priority-pool")

  implicit val ACTOR_TIMEOUT: Timeout = config.getDuration("raven.actor-timeout", TimeUnit.MILLISECONDS)
  
  implicit val ENDPOINT_TIMEOUT: Timeout = config.getDuration("raven.endpoint-timeout", TimeUnit.MILLISECONDS)

  implicit val DB_CHECK: String = config.getString("raven.database.connectionTestQuery")

}
