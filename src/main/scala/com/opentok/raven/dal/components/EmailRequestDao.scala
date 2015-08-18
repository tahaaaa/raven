package com.opentok.raven.dal.components

import com.opentok.raven.model.EmailRequest
import org.slf4j.LoggerFactory
import slick.driver.JdbcProfile
import slick.jdbc.JdbcBackend

import scala.concurrent.Future

trait EmailRequestDao {

  def retrieveRequest(id: String): Future[Option[EmailRequest]]

  def persistRequest(req: EmailRequest): Future[Int]

}

class EmailRequestSlickDao()(implicit driver: JdbcProfile, db: JdbcBackend#Database) extends EmailRequestDao {

  val log = LoggerFactory.getLogger(this.getClass)

  //TODO
  import scala.concurrent.ExecutionContext.Implicits.global
  def persistRequest(req: EmailRequest): Future[Int] = {
    log.info(s"Persisting request with id ${req.id}")
    log.debug("{}", req)
    Future(1)
  }
  //    db.run(sqlu"INSERT INTO email_requests (message) VALUES (${req.message})")

  def retrieveRequest(id: String): Future[Option[EmailRequest]] = {
    Future(None)
  }

}
