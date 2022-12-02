# Camunda RabbitMQ Connector

Find the user documentation in our [Camunda Platform 8 Docs](https://docs.camunda.io/docs/components/integration-framework/connectors/out-of-the-box-connectors/available-connectors-overview).

## Build

```bash
mvn clean package
```

## API

### Input

```json
{
  "authentication": {
    "authType": "credentials",
    "userName": "secrets.USERNAME",
    "password": "secrets.PASSWORD"
  },
  "routing": {
    "exchange": "exchangeName",
    "routingKey": "routingKey",
    "virtualHost": "virtualHostName",
    "hostName": "localhost",
    "port": "5672"
  },
  "message": {
    "body": {"messageBodyKey": "value"},
    "properties": {
      "contentType":"text/plan",
      "headers": {
        "key": "value",
        "key2":"value2"
      }
    }
  }
}
```

```json
{
  "authentication": {
    "authType": "uri",
    "uri": "amqp://userName:password@localhost:5672/virtualHostName"
  },
  "routing": {
    "exchange": "exchange",
    "routingKey": "routingKey"
  },
  "message": {
    "properties": {
      "contentType":"text/plan",
      "headers": {
        "key": "value"
      }
    },
    "body": "some data for send"
  }
}
```

### Output

```json
{
  "result": {
    "statusResult": "success"
  }
}
```

## Test locally

Run unit tests

```bash
mvn clean verify
```

### Test as local Job Worker

Use the [Camunda Connector Runtime](https://github.com/camunda-community-hub/spring-zeebe/tree/master/connector-runtime#building-connector-runtime-bundles) to run your function as a local Job Worker.

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

The element templates can be found in the [element-templates/template-connector.json](element-templates/template-connector.json) file.

## Build a release

Trigger the [release action](./.github/workflows/RELEASE.yml) manually with the version `x.y.z` you want to release and the next SNAPSHOT version.
