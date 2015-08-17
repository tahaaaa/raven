import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.opentok.raven.http.{JsonProtocols, AkkaApi}
import com.opentok.raven.model.Receipt
import fixture.{MockDal, MockSystem}
import org.scalatest.{Matchers, WordSpec}

class ApiSpec extends WordSpec with Matchers with ScalatestRouteTest with JsonProtocols {

  val routeTree = (new MockDal with MockSystem with AkkaApi).routeTree

  "Expose connectivity between service and database" in {
    Get("/v1/monitoring/health?component=dal") ~> routeTree ~> check {
      responseAs[Receipt] should be(Receipt.success)
    }
  }

  "Expose api uptime" in {
    Get("/v1/monitoring/health?component=api") ~> routeTree ~> check {
      responseAs[Receipt] should be(Receipt.success)
    }
  }

  "Expose service uptime" in {
    Get("/v1/monitoring/health?component=service") ~> routeTree ~> check {
      responseAs[Receipt] should be(Receipt.success)
    }
  }

}
