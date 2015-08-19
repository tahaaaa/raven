package com.opentok.raven.fixture

import com.opentok.raven.dal.Dal
import com.opentok.raven.dal.components.{EmailRequestDao, EmailRequestSlickDao}
import slick.driver.{H2Driver, JdbcProfile}
import slick.jdbc.JdbcBackend

class H2Dal extends Dal {
  implicit val driver: JdbcProfile = H2Driver
  implicit val db: JdbcBackend#Database = driver.backend.Database.forConfig("raven.database")
  val emailRequestDao: EmailRequestDao = new EmailRequestSlickDao()
}
