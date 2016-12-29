package com.opentok.raven.model

import com.opentok.raven.Implicits._
import com.opentok.raven.http.JsonProtocol
import com.opentok.raven.model.Email.{EmailAddress, _}
import spray.json.{JsValue, _}

import scala.language.implicitConversions
import scala.util.Try

sealed trait Requestable {

  def id: Option[String]

  def recipients: List[String]
}

/**
  * Service email task request
  *
  * @param to          Email address of the recipient
  * @param template_id template name in resources/templates without extension
  * @param status      status of the request. Check sealed trait [[com.opentok.raven.model.EmailRequest.Status]]
  * @param inject      map of key value pairs to inject to the template
  */
case class EmailRequest(to: String,
                        template_id: String,
                        inject: Option[JsObject],
                        status: Option[EmailRequest.Status],
                        id: Option[String]) extends Requestable {

  @transient
  lazy val recipients = List(to)

  @transient
  lazy val $inject = inject.map(_.fields).getOrElse(Map.empty)

  def validated: EmailRequest = {
    try {
      val email = Email.buildPF(None, "trash@tokbox.com", $inject)
      email.apply(template_id)
    } catch {
      //missing injection parameter
      case e: NoSuchElementException ⇒ throw new MissingInjections($inject, e)
      //invalid template id
      case e: MatchError ⇒ throw new InvalidTemplate(template_id, e)
      case e: Exception ⇒ throw e
    }
    this
  }
}

object EmailRequest {

  sealed trait Status

  case object Pending extends Status

  case object Succeeded extends Status

  case object Failed extends Status

  case object Filtered extends Status

  case object PartiallyFiltered extends Status


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
    if (!_url.startsWith("http://") && (!_url.startsWith("https://")))
      throw new InvalidInjection(_url, "url must start with scheme (http/https) to be parseable by all email clients")

    override def toString: String = _url
  }

  //coerce string to url when required
  implicit def urlToString(url: String): Url = Url(url)

  type HTML = String
  type EmailAddress = String
  type Injections = Map[String, JsValue]

  import com.opentok.raven.http.JsonProtocol._

  //convenience template constructor that uses html.wrap_email_v2
  def wrapTemplate(requestId: Option[String], subject: String, recipient: String,
                   from: String, template: play.twirl.api.Html, fromTemplateId: String,
                   toName: Option[EmailAddress] = None,
                   fromName: Option[String] = None,
                   categories: List[String] = Nil,
                   setReply: Option[EmailAddress] = None,
                   cc: Option[List[EmailAddress]] = None,
                   bcc: Option[List[EmailAddress]] = None,
                   attachments: Option[List[(String, String)]] = None,
                   headers: Option[Map[String, String]] = None): Email =
  Email(requestId, subject, recipient :: Nil, from, html.wrap_email_v2(recipient, template).body,
    Some(fromTemplateId), toName, fromName, Some("raven" :: fromTemplateId :: categories), setReply, cc, bcc, attachments, headers)

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

    case templateId@"password_changed" ⇒
      wrapTemplate(requestId, "Password Changed", recipient, "messages@tokbox.com",
        html.password_changed(),
        templateId, fromName = Some("TokBox"))

    case templateId@"reset_password_instructions" ⇒
      wrapTemplate(requestId, "Reset Password Instructions", recipient, "messages@tokbox.com",
        html.reset_password_instructions(fields %> "reset_password_link"),
        templateId, fromName = Some("TokBox"))

    case templateId@"developer_invitation" ⇒
      wrapTemplate(requestId, "TokBox Account Invitation", recipient, "messages@tokbox.com",
        html.developer_invitation(fields %> "account_name", fields %> "invitation_link"),
        templateId, fromName = Some("TokBox"))

    case templateId@"notification_you_joined_another_account" ⇒
      wrapTemplate(requestId, "TokBox Account Notification", recipient, "messages@tokbox.com",
        html.notification_you_joined_another_account(fields %> "account_name", fields %> "account_portal_url"),
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
        html.payment_details_added(fields %> "account_portal_url"),
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
      wrapTemplate(requestId, "Verify your payment information", recipient, "messages@tokbox.com",
        html.payment_failed(fields.extract[Long]("next_unix_ms"), fields %> "account_portal_url"),
        templateId, fromName = Some("TokBox"))

    case templateId@"account_deleted" ⇒
      wrapTemplate(requestId, "Account Deleted", recipient, "messages@tokbox.com",
        html.account_deleted(fields %> "account_name"),
        templateId, fromName = Some("TokBox"))

    case templateId@"user_deleted_from_account" ⇒
      wrapTemplate(requestId, "Account Deleted", recipient, "messages@tokbox.com",
        html.user_deleted_from_account(fields %> "account_name"),
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
        html.harvester(
          fields %> "title",
          fields %> "datafield",
          fields ?> "datafield_1",
          fields ?> "datafield_2",
          fields ?> "datafield_3",
          fields %> "harvester_image_link",
          fields ?> "harvester_analysis_image_link",
          fields %> "harvester_email",
          fields ?> "harvester_email_1",
          fields ?> "harvester_email_2",
          fields ?> "harvester_email_3",
          fields %> "harvester_message",
          fields ?> "harvester_message_1",
          fields ?> "harvester_message_2"),
        templateId, fromName = Some("Business Analytics"))

    case templateId@"tos_production" ⇒
      wrapTemplate(requestId, " TokBox account suspension warning. Your account will be suspended in 24 hours unless we hear from you", recipient, "billing@tokbox.com",
        html.tos_production(fields %> "login_url"),
        templateId, fromName = Some("TokBox"))

    case templateId@"tools_feedback" ⇒
      wrapTemplate(requestId, " New Tools Feedback Received", recipient, "tools-feedback@tokbox.com",
        html.tools_feedback(
          fields ?> "tool_name",
          fields ?> "component",
          fields ?> "rating",
          fields ?> "feedback_body"),
        templateId, fromName = Some("Tokbox Tools Feedback"))

    case templateId@"project_id_interop" ⇒
      wrapTemplate(requestId, "Issue detected in your OpenTok app: API key-session mismatch", recipient, "messages@tokbox.com",
        html.project_id_interop(), templateId)

    case templateId@"error" ⇒
      val component = fields %> "component"
      val owner = fields ?> "owner"
      wrapTemplate(requestId, s"[$component] Error", recipient, owner.getOrElse("messages@tokbox.com"),
        html.error(fields %> "message", component, fields ?> "stack_trace"), templateId)

    case templateId@"hubble_anomaly" ⇒
      val topicId = fields %> "topic_id"
      wrapTemplate(requestId, s"[Hubble] $topicId", recipient, "hubble@tokbox.com",
        html.hubble_anomaly(
          fields %> "message",
          fields %> "results_url", topicId,
          fields.extract[Int]("size"),
          fields %> "name", fields.extract[Long]("topic_version"),
          fields %> "query", fields %> "source",
          fields %> "analysis", fields.extract[Option[Map[String, String]]]("dimensions"),
          fields ?> "time_cut", fields %> "created_at"), templateId)
  }

  def build(requestId: Option[String], templateId: String, injections: Injections, recipient: String): Try[Email] = Try {
    buildPF(requestId, recipient, injections)(templateId)
  }
}

