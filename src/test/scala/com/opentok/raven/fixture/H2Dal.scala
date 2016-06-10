package com.opentok.raven.fixture

import com.opentok.raven.dal.Dal
import com.opentok.raven.dal.components.{EmailRequestDao, EmailRequestSlickDao}
import slick.driver.{H2Driver, JdbcProfile}
import slick.jdbc.{JdbcBackend, StaticQuery}

import scala.io.Source

trait H2Dal extends Dal with TestConfig {
  implicit val driver: JdbcProfile = H2Driver
  implicit val db: JdbcBackend#Database = driver.backend.Database.forConfig("raven.database")
  val emailRequestDao: EmailRequestDao = new EmailRequestSlickDao()

  //load schema into H2
  val schema =
    Source.fromURI(this.getClass.getClassLoader.getResource("schema.sql").toURI).mkString
  db withSession { implicit session: driver.Backend#Session =>
    StaticQuery.updateNA(schema).execute
  }
}
