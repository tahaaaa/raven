# Opentok Raven

## Service

```javascript
GET /v1/monitoring/health?component=<component>

<receipt>


POST /v1/priority

 -> <EmailRequest>
 <- <Receipt>


POST /v1/certified

 -> <EmailRequest> || [ <EmailRequest> .. N ]
 <- <Receipt>

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
