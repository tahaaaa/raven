package com.opentok.raven.fixture

import java.io.File

import com.opentok.raven.dal.Dal
import com.opentok.raven.dal.components.{EmailRequestDao, EmailRequestSlickDao}
import slick.driver.{H2Driver, JdbcProfile}
import slick.jdbc.{StaticQuery, JdbcBackend}

import scala.io.Source
import scala.util.Success

//TODO insert schema
//TODO SHTMPAPI HEADERS FOR CATEGORIES?
class H2Dal extends Dal {
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
