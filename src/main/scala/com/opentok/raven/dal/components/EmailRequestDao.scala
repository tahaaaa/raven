package com.opentok.raven.dal.components

import com.opentok.raven.model.EmailRequest
import org.slf4j.LoggerFactory
import slick.driver.JdbcProfile
import slick.jdbc.JdbcBackend

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

trait EmailRequestDao {

  def retrieveRequest(id: String)(implicit ctx: ExecutionContext): Future[Option[EmailRequest]]

  def persistRequest(req: EmailRequest): Future[Int]

}

class EmailRequestSlickDao()(implicit driver: JdbcProfile, db: JdbcBackend#Database)
  extends EmailRequestDao {

  import driver.api._
  import spray.json._

  val log = LoggerFactory.getLogger(this.getClass)

  //custom mappers
  private def statusToString(status: EmailRequest.Status): String =
    status.toString.toLowerCase

  private def stringToStatus(str: Option[String]): EmailRequest.Status = str match {
    case Some("pending") ⇒ EmailRequest.Pending
    case Some("failed") ⇒ EmailRequest.Failed
    case Some("succeeded") ⇒ EmailRequest.Succeeded
    case Some(u) ⇒ throw new Exception(s"Stored status $u not recognized!")
    //neither can't happen, not nullable enforced via db schema
    case None ⇒ throw new Exception("Email request status is not nullable!")
  }

  private def injectToString(obj: JsObject): Option[String] =
    Try(obj.compactPrint).toOption

  private def stringToInject(str: Option[String]): Option[JsObject] =
    Try(str.map(_.parseJson.asJsObject)).toOption.flatten


  def persistRequest(req: EmailRequest): Future[Int] = {
    log.info(s"Persisting request with id ${req.id}")
    log.debug("{}", req)

    val inject: Option[String] = req.inject.flatMap(injectToString)
    val status: Option[String] = req.status.map(statusToString)
    //only works with mysql drivers
    db.run( sqlu"""
  INSERT INTO email_requests (request_id, recipient, template_id, status, inject)
  VALUES (${req.id}, ${req.to}, ${req.template_id}, $status, $inject)
  ON DUPLICATE KEY UPDATE
  recipient = ${req.to},
  template_id = ${req.template_id},
  status = $status,
  inject = $inject,
  updated_at = CURRENT_TIMESTAMP()""")
  }

  def retrieveRequest(id: String)(implicit ctx: ExecutionContext): Future[Option[EmailRequest]] = {
    log.info(s"Attempting to retrieve request with id $id")
    db.run(sql"""
      SELECT recipient, template_id, inject, status, request_id
      FROM email_requests
      WHERE request_id = $id""".as[(String, String, Option[String], Option[String], Option[String])])
      .map(_.headOption.map {
      case (recipient, template_id, inject ,status, _) ⇒
        EmailRequest.apply(recipient, template_id, stringToInject(inject),
          Some(stringToStatus(status)), Some(id))
    })
  }

}
