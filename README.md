# Opentok Raven

## Service

```javascript
GET /v1/monitoring/health?component=<component>

<receipt>


POST /v1/priority/send

<EmailRequest> -> <Receipt>


POST /v1/priority/send_batch

[ <EmailRequest> ] -> <Receipt>


POST /v1/certified/send

<EmailRequest> -> <Receipt>


POST /v1/certified/send_batch

[ <EmailRequest> ] -> <Receipt>
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
