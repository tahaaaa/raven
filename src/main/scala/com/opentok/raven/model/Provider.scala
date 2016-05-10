package com.opentok.raven.model

import scala.concurrent.{Future, ExecutionContext}

/**
 * SMTP provider interface
 */
trait Provider {

  def send(em: Email)(implicit ctx: ExecutionContext): Future[Receipt]

}
