package com.opentok.raven.dal

import com.opentok.raven.dal.components.{EmailRequestDao, EmailRequestSlickDao}
import slick.driver.JdbcProfile
import slick.jdbc.JdbcBackend

trait Dal {
  val driver: JdbcProfile
  val db: JdbcBackend#Database
  val emailRequestDao: EmailRequestDao
}

trait MysqlDal extends Dal {
  implicit val driver: JdbcProfile = slick.driver.MySQLDriver
  implicit val db: JdbcBackend#Database = driver.backend.Database.forConfig("hermes.database")
  val emailRequestDao: EmailRequestDao = new EmailRequestSlickDao()
}
