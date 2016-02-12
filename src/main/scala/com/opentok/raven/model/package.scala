package com.opentok.raven

import java.util.UUID

import com.opentok.raven.Implicits._
import com.opentok.raven.http.JsonProtocol
import com.opentok.raven.model.Email._
import spray.json.{JsValue, _}

import scala.util.Try


package object model {

  //marker trait for validation rejections
  sealed trait RavenRejection

  class InvalidTemplate(template_id: String, cause: Throwable)
    extends Exception(s"invalid template id '$template_id'", cause) with RavenRejection

  class MissingInjections(injects: Map[String, JsValue], cause: Throwable)
    extends Exception(s"missing inject in $injects", cause) with RavenRejection

  class InvalidInjection(value: String, msg: String)
    extends Exception(s"invalid value '$value': $msg") with RavenRejection

  sealed trait Requestable {
    val id: Option[String]
  }

  /**
   * Service email task request
   * @param to Email address of the recipient
   * @param template_id template name in resources/templates without extension
   * @param status status of the request. Check sealed trait [[com.opentok.raven.model.EmailRequest.Status]]
   * @param inject map of key value pairs to inject to the template
   */
  case class EmailRequest(to: String,
                          template_id: String,
                          inject: Option[JsObject],
                          status: Option[EmailRequest.Status],
                          id: Option[String]) extends Requestable {

    @transient
    lazy val $inject = inject.map(_.fields).getOrElse(Map.empty)

    @transient
    lazy val json: JsObject = {
      JsonProtocol.requestJsonFormat.write(this).asJsObject
    }

    def validate[T](block: () ⇒ T) =
      try {
        block()
      } catch {
        //missing injection parameter
        case e: NoSuchElementException ⇒ throw new MissingInjections($inject, e)
        //invalid template id
        case e: MatchError ⇒ throw new InvalidTemplate(template_id, e)
        case e: Exception ⇒ throw e
      }

    def validated: EmailRequest = {
      val email = Email.buildPF(None, "trash@tokbox.com", $inject)
      validate(() ⇒ email.isDefinedAt(template_id))
      validate(() ⇒ email.apply(template_id))
      this
    }

  }

  object EmailRequest {

    //transforms an incoming request without id and status
    val fillInRequest = { req: EmailRequest ⇒
      req.copy(
        id = Some(UUID.randomUUID.toString),
        status = Some(EmailRequest.Pending)
      )
    }

    sealed trait Status

    case object Pending extends Status

    case object Succeeded extends Status

    case object Failed extends Status


    implicit object EmailRequestStatusFormat extends RootJsonFormat[EmailRequest.Status] {
      def write(obj: EmailRequest.Status) = JsString(obj.toString)

      def read(json: JsValue): EmailRequest.Status = json match {
        case JsString("Pending") ⇒ Pending
        case JsString("Succeeded") ⇒ Succeeded
        case JsString("Failed") ⇒ Failed
        case s ⇒ throw new SerializationException(s"Unrecognized EmailReceipt.Status '$s'")
      }
    }

  }

  case class Email(
                    id: Option[String],
                    subject: String,
                    recipients: List[EmailAddress],
                    from: EmailAddress,
                    html: HTML,
                    fromTemplateId: Option[String] = None,
                    toName: Option[EmailAddress] = None,
                    fromName: Option[String] = None,
                    categories: Option[List[String]] = None,
                    setReply: Option[EmailAddress] = None,
                    cc: Option[List[EmailAddress]] = None,
                    bcc: Option[List[EmailAddress]] = None,
                    attachments: Option[List[(String, String)]] = None,
                    headers: Option[Map[String, String]] = None
                    ) extends Requestable

  object Email {

    //type to enforce url to start with scheme
    case class Url(_url: String) {
      if (!_url.startsWith("http://"))
        throw new InvalidInjection(_url, "url must start with 'http://' to be parseable by all email clients")

      override def toString: String = _url
    }

    //coerce string to url when required
    implicit def urlToString(url: String): Url = Url(url)

    type HTML = String
    type EmailAddress = String
    type Injections = Map[String, JsValue]

    import com.opentok.raven.http.JsonProtocol._

    //convenience template constructor that uses html.wrap_email_v1
    def wrapTemplate(requestId: Option[String], subject: String, recipient: String,
                     from: String, template: play.twirl.api.Html, fromTemplateId: String,
                     toName: Option[EmailAddress] = None,
                     fromName: Option[String] = None,
                     categories: Option[List[String]] = None,
                     setReply: Option[EmailAddress] = None,
                     cc: Option[List[EmailAddress]] = None,
                     bcc: Option[List[EmailAddress]] = None,
                     attachments: Option[List[(String, String)]] = None,
                     headers: Option[Map[String, String]] = None): Email =
      Email(requestId, subject, recipient :: Nil, from, html.wrap_email_v2(recipient, template).body,
        Some(fromTemplateId), toName, fromName, categories, setReply, cc, bcc, attachments, headers)

    //decoupled from build to check at runtime what templates are available
    def buildPF(requestId: Option[String], recipient: String,
                fields: Map[String, JsValue]): PartialFunction[String, Email] = {

      case templateId@"confirmation_instructions" ⇒
        wrapTemplate(requestId, "Confirmation Instructions", recipient, "messages@tokbox.com",
          html.confirmation_instructions(fields %> "confirmation_url"),
          templateId, fromName = Some("TokBox"))

      case templateId@"repeated_email_attempt" ⇒
        wrapTemplate(requestId, "Repeated Email Attempt", recipient, "messages@tokbox.com",
          html.repeated_email_attempt(fields %> "reset_password_link"),
          templateId, fromName = Some("TokBox"))

      case templateId@"reset_password_instructions" ⇒
        wrapTemplate(requestId, "Reset Password Instructions", recipient, "messages@tokbox.com",
          html.reset_password_instructions(fields %> "reset_password_link"),
          templateId, fromName = Some("TokBox"))

      case templateId@"developer_invitation" ⇒
        wrapTemplate(requestId, "TokBox Account Invitation", recipient, "messages@tokbox.com",
          html.developer_invitation(fields %> "account_name", fields %> "invitation_link"),
          templateId, fromName = Some("TokBox"))

      case templateId@"account_expiration_warning" ⇒
        wrapTemplate(requestId, "Account Expiration Warning", recipient, "messages@tokbox.com",
          html.account_expiration_warning(fields %> "login_url"),
          templateId, fromName = Some("TokBox"))

      case templateId@"account_expiration_confirmation" ⇒
        wrapTemplate(requestId, "Account Expiration Confirmation", recipient, "messages@tokbox.com",
          html.account_expiration_confirmation(),
          templateId, fromName = Some("TokBox"))

      case templateId@"account_suspended" ⇒
        wrapTemplate(requestId, "Account Suspended", recipient, "messages@tokbox.com",
          html.account_suspended(fields %> "login_url"),
          templateId, fromName = Some("TokBox"))

      case templateId@"payment_details_added" ⇒
        wrapTemplate(requestId, "Payment Details Added", recipient, "messages@tokbox.com",
          html.payment_details_added(),
          templateId, fromName = Some("TokBox"))

      case templateId@"email_change_confirmation" ⇒
        wrapTemplate(requestId, "Email Change Confirmation", recipient, "messages@tokbox.com",
          html.email_change_confirmation(fields %> "unconfirmed_email",
            fields %> "confirmed_email", fields %> "confirmation_url"),
          templateId, fromName = Some("TokBox"))

      case templateId@"payment_successful" ⇒
        wrapTemplate(requestId, "Payment Successful", recipient, "messages@tokbox.com",
          html.payment_successful(fields.extract[Float]("amount"), fields %> "currency"),
          templateId, fromName = Some("TokBox"))

      case templateId@"payment_failed" ⇒
        wrapTemplate(requestId, "Payment Failed", recipient, "messages@tokbox.com",
          html.payment_failed(fields.extract[Int]("try_num"),
            fields.extract[Long]("next_unix_ms"), fields %> "account_portal_url"),
          templateId, fromName = Some("TokBox"))

      case templateId@"account_deleted" ⇒
        wrapTemplate(requestId, "Account Deleted", recipient, "messages@tokbox.com",
          html.account_deleted(
            fields.get("last_invoice_amount").map(_.convertTo[Float]),
            fields ?> "last_invoice_currency"
          ),
          templateId, fromName = Some("TokBox"))

      case templateId@"support_plan_upgrade" ⇒
        wrapTemplate(requestId, "Support Plan Upgrade", recipient, "messages@tokbox.com",
          html.support_plan_upgrade(), templateId, fromName = Some("TokBox"))

      case templateId@"archive_upload_failure" ⇒
        wrapTemplate(requestId, "Archive Upload Failure", recipient, "messages@tokbox.com",
          html.archive_upload_failure(
            fields %> "session_id", fields %> "archive_id",
            fields ?> "archive_name", fields.extract[Long]("started_unix_ms")
          ),
          templateId, fromName = Some("TokBox"))

      case templateId@"test" ⇒
        wrapTemplate(requestId, "Raven Test", recipient, "analytics@tokbox.com",
          html.test(fields %> "a", fields.extract[Int]("b")),
          templateId, fromName = Some("TokBox"))

      case templateId@"harvester" ⇒
        wrapTemplate(requestId, "Harvester Email", recipient, "analytics@tokbox.com",
          html.harvester(fields %> "datafield", fields %> "harvester_message"),
          templateId, fromName = Some("Business Analytics"))

    }

    def build(requestId: Option[String], templateId: String, injections: Injections, recipient: String): Try[Email] = Try {
      buildPF(requestId, recipient, injections)(templateId)
    }

  }

}
