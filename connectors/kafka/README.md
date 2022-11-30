# Camunda Kafka Connector

Find the user documentation in our [Camunda Platform 8 Docs](https://docs.camunda.io/docs/components/integration-framework/connectors/out-of-the-box-connectors/available-connectors-overview).

## Build

```bash
mvn clean package
```

## API

### Input

```json
{
  "authentication":{
    "username":"secrets.KAFKA_USER_NAME",
    "password":"secrets.KAFKA_PASSWORD"
  },
  "topic":{
    "bootstrapServers":"secrets.KAFKA_BOOTSTRAP_SERVERS",
    "topicName":"secrets.KAFKA_TOPIC_NAME"
  },
  "message":{
    "key":"document-id-1234567890",
    "value": {"documentId": "1234567890", "signedBy": "Tester Testerson", "contentBase64": "Q2FtdW5kYSBLYWZrYSBDb25uZWN0b3I="}
  },
  "additionalProperties": {
    "delivery.timeout.ms":"25000",
    "request.timeout.ms":"25000"
  }
}
```

### Output

```json
{
  "result": {
    "topic":"topic_0",
    "timestamp":1665927163361,
    "offset":9,
    "partition":1
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

The element templates can be found in the [element-templates/kafka-connector.json](element-templates/kafka-connector.json) file.

## Build a release

Trigger the [release action](./.github/workflows/RELEASE.yml) manually with the version `x.y.z` you want to release and the next SNAPSHOT version.
