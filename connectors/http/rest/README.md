![REST Outbound Connector connector icon](data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMTgiIGhlaWdodD0iMTgiIHZpZXdCb3g9IjAgMCAxOCAxOCIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj4KPHBhdGggZD0iTTE3LjAzMzUgOC45OTk5N0MxNy4wMzM1IDEzLjQ0NzUgMTMuNDI4MSAxNy4wNTI5IDguOTgwNjUgMTcuMDUyOUM0LjUzMzE2IDE3LjA1MjkgMC45Mjc3NjUgMTMuNDQ3NSAwLjkyNzc2NSA4Ljk5OTk3QzAuOTI3NzY1IDQuNTUyNDggNC41MzMxNiAwLjk0NzA4MyA4Ljk4MDY1IDAuOTQ3MDgzQzEzLjQyODEgMC45NDcwODMgMTcuMDMzNSA0LjU1MjQ4IDE3LjAzMzUgOC45OTk5N1oiIGZpbGw9IiM1MDU1NjIiLz4KPHBhdGggZD0iTTQuOTMxMjYgMTQuMTU3MUw2Ljc4MTA2IDMuNzE0NzFIMTAuMTM3NUMxMS4xOTE3IDMuNzE0NzEgMTEuOTgyNCAzLjk4MzIzIDEyLjUwOTUgNC41MjAyN0MxMy4wNDY1IDUuMDQ3MzYgMTMuMzE1IDUuNzMzNTggMTMuMzE1IDYuNTc4OTJDMTMuMzE1IDcuNDQ0MTQgMTMuMDcxNCA4LjE1NTIyIDEyLjU4NDEgOC43MTIxNUMxMi4xMDY3IDkuMjU5MTMgMTEuNDU1MyA5LjYzNzA1IDEwLjYyOTggOS44NDU5TDEyLjA2MTkgMTQuMTU3MUgxMC4zMzE1TDkuMDMzNjQgMTAuMDI0OUg3LjI0MzUxTDYuNTEyNTQgMTQuMTU3MUg0LjkzMTI2Wk03LjQ5NzExIDguNTkyODFIOS4yNDI0OEM5Ljk5ODMyIDguNTkyODEgMTAuNTkwMSA4LjQyMzc0IDExLjAxNzcgOC4wODU2MUMxMS40NTUzIDcuNzM3NTMgMTEuNjc0MSA3LjI2NTEzIDExLjY3NDEgNi42Njg0MkMxMS42NzQxIDYuMTkxMDYgMTEuNTI0OSA1LjgxODExIDExLjIyNjUgNS41NDk1OUMxMC45MjgyIDUuMjcxMTMgMTAuNDU1OCA1LjEzMTkgOS44MDkzNiA1LjEzMTlIOC4xMDg3NEw3LjQ5NzExIDguNTkyODFaIiBmaWxsPSJ3aGl0ZSIvPgo8L3N2Zz4K)
# REST Outbound Connector
Invoke REST API
# Camunda HTTP JSON Connector

Find the user documentation in our [Camunda](https://docs.camunda.io/docs/components/connectors/protocol/rest/).

## Build

```bash
mvn clean package
```

## API

### Input

```json
{
  "method": "post",
  "url": "https://httpbin.org/anything",
  "queryParameters": {
    "q": "test",
    "priority": 12
  },
  "headers": {
    "User-Agent": "http-connector-demo"
  },
  "body": {
    "customer": {
      "id": 1231231,
      "name": "Jane Doe",
      "email": "jane.doe@exampe.com"
    }
  }
}
```

### Output

The response will contain the status code, the headers and the body of the response of the HTTP service.

```json
{
  "body": {
    "args": {
      "priority": "12",
      "q": "test"
    },
    "data": "{\"customer\":{\"id\":1231231.0,\"name\":\"Jane Doe\",\"email\":\"jane.doe@exampe.com\"}}",
    "files": {},
    "form": {},
    "headers": {
      "Accept-Encoding": "gzip",
      "Content-Length": "77",
      "Content-Type": "application/json",
      "Host": "httpbin.org",
      "User-Agent": "http-connector-demo Google-HTTP-Java-Client/1.41.4 (gzip)",
      "X-Amzn-Trace-Id": "Root=1-623105a8-35f88bac0c7f1dcf0d2c8aa2"
    },
    "json": {
      "customer": {
        "email": "jane.doe@exampe.com",
        "id": 1231231.0,
        "name": "Jane Doe"
      }
    },
    "method": "POST",
    "origin": "79.202.43.240",
    "url": "https://httpbin.org/anything?q=test&priority=12"
  },
  "headers": {
    "access-control-allow-credentials": "true",
    "access-control-allow-origin": "*",
    "connection": "keep-alive",
    "content-length": 733,
    "content-type": "application/json",
    "date": "Tue, 15 Mar 2022 21:31:20 GMT",
    "server": "gunicorn/19.9.0"
  },
  "status": 200
}
```

### Input (Basic)

```json
{
  "method": "get",
  "url": "https://httpbin.org/basic-auth/user/password",
  "authentication": {
    "type": "basic",
    "username": "{{secrets.USERNAME}}",
    "password": "{{secrets.PASSWORD}}"
  }
}
```

### Output (Bearer Token)

```json
{
  "method": "get",
  "url": "https://httpbin.org/bearer",
  "authentication": {
    "type": "bearer",
    "token": "{{secrets.TOKEN}}"
  }
}
```

### Input (OAuth 2.0)

```json
{
  "method": "post",
  "url": "https://youroauthclientdomainname.eu.auth0.com/oauth/token",
  "authentication": {
    "oauthTokenEndpoint":"{{secrets.OAUTH_TOKEN_ENDPOINT_KEY}}",
    "scopes": "read:clients read:users",
    "audience":"{{secrets.AUDIENCE_KEY}}",
    "clientId":"{{secrets.CLIENT_ID_KEY}}",
    "clientSecret":"{{secrets.CLIENT_SECRET_KEY}}",
    "type": "oauth-client-credentials-flow",
    "clientAuthentication":"{{secrets.CLIENT_AUTHENTICATION_KEY}}"
  }
}
```

### Output (Access Token)

```json
{
  "access_token":"eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6IlUtN2N6WG1sMzljUFNfUnlQQkNMWCJ9.kjhwfjkhfejkrhfbwjkfbhetcetc",
  "scope":"read:clients create:users",
  "expires_in":86400,
  "token_type":"Bearer"
}
```
### Error codes

The Connector will fail on any non-2XX HTTP status code in the response. This error status code will be passed on as error code, e.g. "404".

## :lock: Use proxy-mechanism

> :warning: Proxy mode is currently only supported in Camunda 8 SaaS environment.

> :warning: This is for Camunda internal use only, do not mistake this with the [general proxy configuration](https://docs.camunda.io/docs/components/connectors/protocol/rest/#configure-a-proxy-server-in-self-managed)
> available in
> Self-Managed.
You can configure the HTTP JSON Connector to do any outgoing HTTP call via a proxy. This proxy should be effectively
also an HTTP JSON Connector

For example, you can build the following runtime architecture:

```
   Camunda Process --> HTTP Connector (Proxy-mode) --> HTTP Connector --> Endpoint
 [ Camunda Network, e.g. K8S                      ]  [ Separate network, e.g. Google Function ]
```

Now, any call via the Http Connector will be just forwarded to a specified hardcoded URL. And this proxy does the real call then.
This avoids that you could reach internal endpoints in your Camunda network (e.g. the current Kubernetes cluster).

Just set the following property to enable proxy mode for the connector, e.g. in application.properties when using the Spring-based runtime:

```properties
camunda.connector.http.proxy.url=https://someUrl/
```

You can also set this via environment variables:

```
CAMUNDA_CONNECTOR_HTTP_PROXY_URL=https://someUrl/
```

If the other party requiring OAuth for authentication, you need to set the following environment property:

```shell
GOOGLE_APPLICATION_CREDENTIALS=...
```

### :lock: Test the Connector locally with Google Cloud Function as a proxy

Run the [:lock:connector-proxy-saas](https://github.com/camunda/connector-proxy-saas) project locally as described in its [:lock:README](https://github.com/camunda/connector-proxy-saas#usage).

Set the specific property or environment variable to enable proxy mode as described above.

## Element Template

This Connector is a **Protocol Connector**. It is used by multiple out-of-the-box Connector templates.

The generic HTTP JSON Connector element template can be found in
the [element-templates/http-json-connector.json](element-templates/http-json-connector.json) file.

Additional Connector templates based on the HTTP JSON Connector:
- [Automation Anywhere Connector](../automation-anywhere)
- [Blue Prism Connector](../blue-prism)
- [UiPath Connector](../uipath)


## Properties
| Name   | Type     | Required | Description | Example        |
| ------ | -------- | -------- | ----------- | -------------- |
| Method | Dropdown | Yes      |             | ```{ }```      |
| URL    | String   | Yes      |             | ```"string"``` |
## Result
The following json structure will be returned by the Connector and can be
used in the result expression.

```json
{
  "body" : {
    "order" : {
      "id" : "123",
      "total" : "100.00€"
    }
  },
  "document" : {
    "contentHash" : "hash",
    "documentId" : "977c5cbf-0f19-4a76-a8e1-60902216a07b",
    "metadata" : {
      "contentType" : "application/pdf",
      "customProperties" : {
        "key" : "value"
      },
      "fileName" : "theFileName.pdf",
      "processInstanceKey" : 0,
      "size" : 516554
    },
    "storeId" : "theStoreId",
    "camunda.document.type" : "camunda"
  },
  "headers" : {
    "Content-Type" : "application/json"
  },
  "status" : 200
}
```

The body can be accessed via FEEL:
```json
= body.order.id
```
leading to the following result
```json
"123"
```


| Connector Info            |                                                                       |
| ---                       | ---                                                                   |
| Type                      | io.camunda:http-json:1                                                            |
| Version                   | 11                                                         |
| Supported element types   |     |

