package com.opentok.raven

import akka.http.scaladsl.Http
import com.opentok.raven.dal.MysqlDal
import com.opentok.raven.http.AkkaApi
import com.opentok.raven.service.{AkkaService, AkkaSystem}
import com.typesafe.config.ConfigFactory

import scala.util.{Failure, Success}

object Boot extends FromResourcesConfig(ConfigFactory.load())
with App with MysqlDal with AkkaSystem with AkkaService with AkkaApi with RavenLogging {

  import system.dispatcher

  Http().bindAndHandle(handler = routeTree, interface = HOST, port = PORT)

  testDalConnectivity().andThen {
    case s: Success[_] ⇒
      log.info(s"raven service started and listening on $HOST:$PORT; PRD:$PRD; " +
        s"restrictTo: $RESTRICT_TO; max-retries=$MAX_RETRIES; deferrer=$DEFERRER; " +
        s"actor-timeout:$ACTOR_TIMEOUT; endpoint-timeout:$ENDPOINT_TIMEOUT")
    case Failure(e) ⇒
      log.error(e, s"data access layer verification failed")
      //terminate service
      system.terminate()
  }
}
