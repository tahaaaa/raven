# Opentok Raven
![](http://orig01.deviantart.net/11fe/f/2010/217/7/8/giant_raven_flying_by_furansu.gif)

To see the list of available templates go to [https://raven-tbdev.tokbox.com/v1/debug/template](https://raven-tbdev.tokbox.com/v1/debug/template) or
jump to [Run](#run) section and boot up a local instance. Check [Templates](#templates) section to find out how to use them.

## Develop
1. Make sure you have installed sbt, docker and docker-compose
1. Create a configuration override file: `cp src/main/resources/reference.conf src/main/resources/application.conf`
2. Request a Sendgrid api-key and add it to `sendgrid.api_key` in `application.conf`.
3. `docker-compose up` to start mysql db
4. `sbt run` or `sbt reStart` to build the project from source files
5. [Try](#try)

Note that by default only emails that match `.*@tokbox.com` will be sent. This can be turned off by setting `prd` to `true`.

## Service

```javascript
POST /v1/priority

→ <EmailRequest>
← <Receipt>


POST /v1/certified

→ <EmailRequest> || <Email>
← <Receipt>


GET /v1/monitoring/health?component=<component>

← <receipt>


GET /v1/monitoring/pending

← [ <receipt>, ... ]


GET /v1/debug/template

← [ <template_id> .. N ]


GET /v1/debug/template/<template_id>

← Template


GET /v1/debug/template/<template_id>?[injections]

← Html
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

Email
```javascript
{
  "from" : str,
  "recipients" : [ str ],
  "subject" : str,
  "categories" [ str ],
  "html" : str
}
```

## Templates
After booting up go to [http://localhost:9911/v1/debug/template](http://localhost:9911/v1/debug/template) to see a list of available templates. Complete path with a `template_id` to find out how to use template i.e. [http://localhost:9911/v1/debug/template/test](http://localhost:9911/v1/debug/template/test). At the top of the html document, there will be a list of parameters and their types; if there are none, it means that the template doesn't require any parameters. Pass them in query string to see compiled template i.e. [http://localhost:9911/v1/debug/template/test?a=hello&b=1](http://localhost:9911/v1/debug/template/test?a=hello&b=1).

## Try
```
curl -k -XPOST -H 'Content-Type:application/json' -d '{"to":"YOUR_EMAIL@tokbox.com", "template_id":"YOUR_TEMPLATE_ID", "inject" : {"param1":"yay", "param2": "lol"}}' http://localhost:9911/v1/priority
```

## Deploy
Do ` sbt clean assembly && docker build -t opentok/raven:latest . `, then `docker run -d -p 8000:9911 --restart=always --name raven -v path/to/host/resources/folder:/etc/opentok/ -v path/to/host/logs:/var/log/opentok opentok/raven:latest`.
Make sure you place in `path/to/host/resources/folder/` an `application.conf` configuration file to override all default values, including the db ip and port.
