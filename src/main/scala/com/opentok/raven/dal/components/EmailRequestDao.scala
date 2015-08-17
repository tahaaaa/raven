package com.opentok.raven.dal.components

import com.opentok.raven.service.actors.EmailSupervisor.RelayEmailCmd
import slick.driver.JdbcProfile
import slick.jdbc.JdbcBackend

import scala.concurrent.Future

trait EmailRequestDao {

  def persistRequest(req: RelayEmailCmd): Future[Int]

}

class EmailRequestSlickDao()(implicit driver: JdbcProfile, db: JdbcBackend#Database) extends EmailRequestDao {

  import driver.api._

  def persistRequest(req: RelayEmailCmd): Future[Int] =
    db.run(sqlu"INSERT INTO outgoing_emails (message) VALUES (${req.message})")

}
