package com.opentok.raven

import akka.http.scaladsl.Http
import com.opentok.raven.dal.MysqlDal
import com.opentok.raven.http.AkkaApi
import com.opentok.raven.service.AkkaSystem


object Boot extends App with MysqlDal with AkkaSystem with AkkaApi {

  Http().bindAndHandle(handler = routeTree, interface = GlobalConfig.HOST, port = GlobalConfig.PORT)

}
