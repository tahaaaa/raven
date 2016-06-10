package com.opentok.raven.dal

import com.opentok.raven.fixture.H2Dal
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.Await
import scala.concurrent.duration._

class DalSpec extends WordSpec with Matchers {


  "DAL" should {
    "test connectivity to database" in {
      val dal = new H2Dal {}

      assert(Await.result(dal.testDalConnectivity(), 2.seconds) == 1)
    }
  }
}
