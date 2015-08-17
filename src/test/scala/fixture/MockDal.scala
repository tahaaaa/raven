package fixture

import com.opentok.raven.dal.Dal
import com.opentok.raven.dal.components.{EmailRequestDao, EmailRequestSlickDao}
import slick.driver.{H2Driver, JdbcProfile}
import slick.jdbc.JdbcBackend

class MockDal extends Dal {
  implicit val driver: JdbcProfile = H2Driver
  implicit val db: JdbcBackend#Database = driver.backend.Database.forConfig("hermes.database")
  val emailRequestDao: EmailRequestDao = new EmailRequestSlickDao()
}
