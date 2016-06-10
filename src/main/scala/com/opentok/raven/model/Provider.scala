package com.opentok.raven.model

import scala.concurrent.{ExecutionContext, Future}

/**
 * SMTP provider interface
 */
trait Provider {

  def send(em: Email)(implicit ctx: ExecutionContext, rctx: RequestContext): Future[Receipt]

}
