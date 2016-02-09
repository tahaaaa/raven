package com.opentok.raven.dal

import com.opentok.raven.RavenConfig
import com.opentok.raven.dal.components.{EmailRequestDao, EmailRequestSlickDao}
import slick.driver.JdbcProfile
import slick.jdbc.{SetParameter, SQLActionBuilder, JdbcBackend}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Try

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
  this: RavenConfig ⇒

  implicit val driver: JdbcProfile = slick.driver.MySQLDriver
  implicit val db: JdbcBackend#Database = driver.backend.Database.forConfig("raven.database")
  val emailRequestDao: EmailRequestDao = new EmailRequestSlickDao()

  def testDalConnectivity(): Future[Int] = Future.fromTry(Try {
    import driver.api._
    val builder = SQLActionBuilder.apply(Seq(DB_CHECK.replace(";", "")), SetParameter.SetUnit)
    //force timeout
    Await.result(db.run(builder.as[Int].head), 5.seconds)
  })
}
