package com.opentok.raven.dal.components

import com.opentok.raven.model.EmailRequest
import slick.driver.JdbcProfile
import slick.jdbc.JdbcBackend

import scala.concurrent.Future

trait EmailRequestDao {

  def persistRequest(req: EmailRequest): Future[Int]

}

class EmailRequestSlickDao()(implicit driver: JdbcProfile, db: JdbcBackend#Database) extends EmailRequestDao {

  //TODO
  def persistRequest(req: EmailRequest): Future[Int] = ???
  //    db.run(sqlu"INSERT INTO email_requests (message) VALUES (${req.message})")

}
