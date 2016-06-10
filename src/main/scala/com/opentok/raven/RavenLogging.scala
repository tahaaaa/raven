package com.opentok.raven

import build.unstable.tylog.TypedLogging
import org.slf4j.{Logger, LoggerFactory}

trait RavenLogging extends TypedLogging {

  type TraceID = String

  lazy val log: Logger = LoggerFactory.getLogger(this.getClass)

  sealed trait CallType

  case object CompleteRequest extends CallType

  case class HandleEmailRequest(endpoint: String) extends CallType

  case object SuperviseRequest extends CallType

  case object PersistRequestState extends CallType
  
  case object BuildEmail extends CallType

  case object RetrieveRequestState extends CallType

  case object ProviderSendEmail extends CallType

}
