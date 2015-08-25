package com.opentok.raven.dal

import com.opentok.raven.dal.components.{EmailRequestDao, EmailRequestSlickDao}
import slick.driver.JdbcProfile
import slick.jdbc.JdbcBackend

/**
 * Jdbc data access interface
 */
trait Dal {
  val driver: JdbcProfile
  val db: JdbcBackend#Database
  val emailRequestDao: EmailRequestDao
}

/**
 * It implements ``Dal`` by binding the JdbcDriver to Slick's MySQL driver,
 * it loads the database configuration and instantiates the ``EmailRequestDao``.
 */
trait MysqlDal extends Dal {
  implicit val driver: JdbcProfile = slick.driver.MySQLDriver
  implicit val db: JdbcBackend#Database = driver.backend.Database.forConfig("raven.database")
  val emailRequestDao: EmailRequestDao = new EmailRequestSlickDao()
}
