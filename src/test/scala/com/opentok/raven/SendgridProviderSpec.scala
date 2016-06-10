package com.opentok.raven

import com.opentok.raven.model.{RequestContext, Email, EmailRequest, SendgridProvider}
import com.sendgrid.SendGrid
import com.sendgrid.SendGrid.{Response}
import org.joda.time.DateTime
import org.scalatest.{Matchers, WordSpecLike}
import spray.json.{JsNumber, JsString, JsObject}
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class SendgridProviderSpec extends WordSpecLike with Matchers {

  import fixture._

  implicit val rtctx = RequestContext(testRequest, "1")

  class MockSendGridClient extends SendGrid("") {
    var sent: Int = 0
    var recipients: List[String] = Nil

    override def send(email: com.sendgrid.SendGrid.Email): Response = {
      sent += 1
      recipients ++= email.getTos
      new Response(200, "OK")
    }
  }

  val TOKBOX_RECIPIENT = "ernest@tokbox.com"
  val TOKBOX_RECIPIENT2 = "trash+1232315iu458r34uto435ijthgo53@tokbox.com"
  val NON_TOKBOX_RECIPIENT = "tokbox@hotmail.com"
  val NON_TOKBOX_RECIPIENT2 = "ernest+k31jkl312jlk32j1lk2@unstable.build"

  val tokboxRequest = EmailRequest(TOKBOX_RECIPIENT, "test",
    Some(JsObject(Map("a" → JsString(s"UNIT TEST RUN AT ${new DateTime().toString}"),
      "b" → JsNumber(1)))), None, Some("aaaaa"))

  val tokboxRequest2 = tokboxRequest.copy(to = TOKBOX_RECIPIENT2)

  val nonTokboxRequest = tokboxRequest.copy(to = NON_TOKBOX_RECIPIENT)

  val tokboxEmail =
    Email.build(tokboxRequest.id, tokboxRequest.template_id, tokboxRequest.$inject, tokboxRequest.to)

  val tokboxEmail2 =
    Email.build(tokboxRequest2.id, tokboxRequest2.template_id, tokboxRequest2.$inject, tokboxRequest2.to)

  val nonTokboxEmail =
    Email.build(nonTokboxRequest.id, nonTokboxRequest.template_id, nonTokboxRequest.$inject, nonTokboxRequest.to)

  val mixedTokboxAndNonTokboxEmail =
    new Email(
      id = Some("id"),
      subject = "mixed emails subject",
      recipients = TOKBOX_RECIPIENT :: NON_TOKBOX_RECIPIENT2 :: TOKBOX_RECIPIENT2 :: Nil,
      from = "analytics@tokbox.com",
      html = "<h1>HELLO</h1>"
    )

  "When 'prd' flag is off, emails should be restricted to 'restrict-to' regex" in {
    val sendgrid = new MockSendGridClient()
    val provider = new SendgridProvider(sendgrid, false, Some(".*@tokbox.com"))
    Await.result(provider.send(tokboxEmail.get), 2.seconds)
    sendgrid.sent shouldBe 1
    sendgrid.recipients should contain only TOKBOX_RECIPIENT

    Await.result(provider.send(nonTokboxEmail.get), 2.seconds)
    sendgrid.sent shouldBe 1 //no recipients match so no email sent
    sendgrid.recipients should contain only TOKBOX_RECIPIENT

    Await.result(provider.send(tokboxEmail2.get), 2.seconds)
    sendgrid.sent shouldBe 2
    sendgrid.recipients should contain theSameElementsAs (TOKBOX_RECIPIENT :: TOKBOX_RECIPIENT2 :: Nil)

    Await.result(provider.send(mixedTokboxAndNonTokboxEmail), 2.seconds)
    sendgrid.sent shouldBe 3 //2/3 recipient passed regex
    sendgrid.recipients should contain theSameElementsAs (
      TOKBOX_RECIPIENT2 :: TOKBOX_RECIPIENT :: TOKBOX_RECIPIENT2 :: TOKBOX_RECIPIENT :: Nil)
  }

  "When 'prd' flag is true, emails should be sent regardless of 'restrict-to' regex" in {
    val sendgrid = new MockSendGridClient()
    val sendgrid2 = new MockSendGridClient()
    val provider = new SendgridProvider(sendgrid, true, Some(".*@tokbox.com"))
    val provider2 = new SendgridProvider(sendgrid2, true, None)

    Await.result(provider.send(tokboxEmail.get), 2.seconds)
    sendgrid.sent shouldBe 1
    sendgrid.recipients should contain only TOKBOX_RECIPIENT

    Await.result(provider.send(nonTokboxEmail.get), 2.seconds)
    sendgrid.sent shouldBe 2
    sendgrid.recipients should contain theSameElementsAs TOKBOX_RECIPIENT :: NON_TOKBOX_RECIPIENT :: Nil

    Await.result(provider2.send(tokboxEmail2.get), 2.seconds)
    sendgrid2.sent shouldBe 1
    sendgrid2.recipients should contain only TOKBOX_RECIPIENT2

    Await.result(provider2.send(nonTokboxEmail.get), 2.seconds)
    sendgrid2.sent shouldBe 2
    sendgrid2.recipients should contain theSameElementsAs TOKBOX_RECIPIENT2 :: NON_TOKBOX_RECIPIENT :: Nil

    Await.result(provider2.send(mixedTokboxAndNonTokboxEmail), 2.seconds)
    sendgrid2.sent shouldBe 3
    sendgrid2.recipients should contain allOf(
      TOKBOX_RECIPIENT2, NON_TOKBOX_RECIPIENT, NON_TOKBOX_RECIPIENT2, TOKBOX_RECIPIENT)
  }
}
