# Camunda HTTP JSON Connector

Find the user documentation in our [Camunda Platform 8 Docs](https://docs.camunda.io/docs/components/integration-framework/connectors/out-of-the-box-connectors/rest/).

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

### Input (Basic Auth)

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

### Error codes

The Connector will fail on any non-2XX HTTP status code in the response. This error status code will be passed on as error code, e.g. "404".

## Use proxy-mechanism

You can configure the HTTP JSON Connector to do any outgoing HTTP call via a proxy. This proxy should be effectively also an HTTP JSON Connector
running in a different environment.

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



## Test locally

### Run unit tests

Run unit tests

```bash
mvn clean verify
```

### Test with local runtime

Use the [Camunda Connector Runtime](https://github.com/camunda-community-hub/spring-zeebe/tree/master/connector-runtime#building-connector-runtime-bundles) to run your function as a local Java application.

In your IDE you can also simply navigate to the `LocalContainerRuntime` class in test scope and run it via your IDE.

### :lock: Test as local Google Cloud Function

> **Warning**
> This is Camunda-internal only. The Maven profile `cloud-function` accesses an internal artifact.

Build as Google Cloud Function

```bash
mvn function:run -Pcloud-function
```

See also the [:lock:Camunda Cloud Connector Run-Time](https://github.com/camunda/connector-runtime-cloud) on how your function
is run as a Google Cloud Function.

## Element Template

The element templates can be found in
the [element-templates/http-json-connector.json](element-templates/http-json-connector.json) file.

## Build a release

Trigger the [release action](./.github/workflows/RELEASE.yml) manually with the version `x.y.z` you want to release and the next SNAPSHOT version.
