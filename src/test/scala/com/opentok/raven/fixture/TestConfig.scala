package com.opentok.raven.fixture

import akka.util.Timeout
import com.opentok.raven.RavenConfig

import scala.concurrent.duration._

trait TestConfig extends RavenConfig {
  override val HOST: String = "localhost"
  override val PORT: Int = 9911
  override val ENDPOINT_TIMEOUT: Timeout = 6.seconds
  override val SENDGRID_API_KEY: String = ""
  override implicit val ACTOR_TIMEOUT: Timeout = 2.seconds
  override val CERTIFIED_POOL: Int = 1
  override val MAX_RETRIES: Int = 3
  override val DEFERRER: Int = 1
  override val DB_CHECK: String = "select 1;"
  override val PRIORITY_POOL: Int = 1
  override val ACTOR_INNER_TIMEOUT: Timeout = 4.seconds
}
