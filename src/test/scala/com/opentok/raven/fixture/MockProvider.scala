package com.opentok.raven.fixture

import com.opentok.raven.model.{Receipt, Email, Provider}

import scala.concurrent.{Future, ExecutionContext}

class MockProvider(rec: Receipt) extends Provider {

  @volatile
  var right = 0

  override def send(em: Email)(implicit ctx: ExecutionContext): Future[Receipt] = {
    right += 1
    Future.successful(rec)
  }
}

class UnresponsiveProvider extends Provider {

  @volatile
  var received = 0

  override def send(em: Email)(implicit ctx: ExecutionContext): Future[Receipt] = {
    received += 1
    Future.failed(new Exception("BOOM"))
  }
}
