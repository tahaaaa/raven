package com.opentok.raven

import akka.http.scaladsl.Http
import com.opentok.raven.dal.MysqlDal
import com.opentok.raven.http.AkkaApi
import com.opentok.raven.service.{AkkaService, AkkaSystem}
import com.typesafe.config.ConfigFactory


object Boot extends FromResourcesConfig(ConfigFactory.load())
with App with MysqlDal with AkkaSystem with AkkaService with AkkaApi {

  Http().bindAndHandle(handler = routeTree, interface = HOST, port = PORT)

}
