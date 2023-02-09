# Camunda GraphQL Connector

Find the user documentation in our [Camunda Platform 8 Docs](https://docs.camunda.io/docs/components/integration-framework/connectors/out-of-the-box-connectors/graphql/).

## Build

```bash
mvn clean package
```

## API

### Input

```json
{
  "url": "https://swapi-graphql.netlify.app/.netlify/functions/index",
  "method": "post",
  "query": "query Root($id: ID) {person (id: $id) {id, name}}",
  "variables": "{\"id\": \"cGVvcGxlOjI=\"}"
}
```

### Output

The response will contain the status code, the headers and the body of the response of the GraphQL query response.

```json
{
  "body": {
    "data":{
      "person":
      {
        "id":"cGVvcGxlOjI=", 
        "name":"C-3PO"
      }
    }
  },
  "headers": {
    "access-control-allow-credentials": "true",
    "access-control-allow-origin": "*",
    "connection": "keep-alive",
    "content-length": 56,
    "content-type": "application/json; charset=utf-8",
    "date": "Tue, 15 Mar 2022 21:31:20 GMT",
    "server": "Netlify"
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
    "username": "secrets.USERNAME",
    "password": "secrets.PASSWORD"
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
    "token": "secrets.TOKEN"
  }
}
```

### Input (OAuth 2.0)

```json
{
  "method": "post",
  "url": "https://youroauthclientdomainname.eu.auth0.com/oauth/token",
  "authentication": {
    "oauthTokenEndpoint":"secrets.OAUTH_TOKEN_ENDPOINT_KEY",
    "scopes": "read:clients read:users",
    "audience":"secrets.AUDIENCE_KEY",
    "clientId":"secrets.CLIENT_ID_KEY",
    "clientSecret":"secrets.CLIENT_SECRET_KEY",
    "type": "oauth-client-credentials-flow",
    "clientAuthentication":"secrets.CLIENT_AUTHENTICATION_KEY"
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

## Use proxy-mechanism

The graphQL connector executes the queries/mutations using an HTTP call. You can configure the GraphQL Connector to do any outgoing HTTP call via a proxy. This proxy should be effectively an HTTP JSON Connector
running in a different environment.

For example, you can build the following runtime architecture:

```
   Camunda Process --> GraphQL Connector (Proxy-mode) --> HTTP Connector --> GraphQL Endpoint
 [ Camunda Network, e.g. K8S                         ]  [ Separate network, e.g. Google Function ]
```

Now, any GraphQL query/mutation will be just forwarded to a specified hardcoded URL. And this proxy does the real call then.
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

The element templates can be found in
the [element-templates/graphql-connector.json](element-templates/graphql-connector.json) file.
