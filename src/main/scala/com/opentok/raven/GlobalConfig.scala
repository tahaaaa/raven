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

  val MAX_RETRIES = config.getInt("raven.max_retries")

  implicit val DEFAULT_TIMEOUT: Timeout = config.getDuration("raven.timeout", TimeUnit.MILLISECONDS)

  implicit val DB_CHECK: String = config.getString("raven.database.connectionTestQuery")

}
