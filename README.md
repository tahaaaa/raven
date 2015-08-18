# Opentok Raven

## Service

```javascript
GET /v1/monitoring/health?component=<component>

← <receipt>


POST /v1/priority

→ <EmailRequest>
← <Receipt>


POST /v1/certified

→ <EmailRequest> || [ <EmailRequest> .. N ]
← <Receipt>

```

## Model

Receipt
```javascript
{
  "success": bool,
  "message": str,
  "errors": [ str ]
}
```

EmailRequest
```javascript
{
    "to": str,
    "template_id": str,
    "inject": {
       <key>:<value>
    }
}
```

## Templates 
|template_id|inject| 
|---|---|
|charged_successfully|cents: float|
|billing_failure|next_payment_attempt: int or null| 
|suspended_for_billing_failure|N/A|
|cancel_subscription|N/A|
|cancel_with_prorate| prorate_ammount: float in cents |
|activate_with_cc|N/A|
|repeat_registration_attempt|N/A|
|archive_upload_failed|session_id: str,<br> archive_id: str, <br> archive_name: str,<br> archive_created: unix timestamp |
|invite_developer|name: str,<br> message: str|
|invite_developer_notification| name: str |
|change_email_confirmation|unconfirmed_email: str, <br> old_email: str, <br>confirmation_link: str|
|signup_confirmation|first_api_key: str, <br> confirmation_link: str|
|reset_password_instruction|reset_password_link: str|

