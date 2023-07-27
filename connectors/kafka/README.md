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
    "username":"{{secrets.KAFKA_USER_NAME}}",
    "password":"{{secrets.KAFKA_PASSWORD}}"
  },
  "topic":{
    "bootstrapServers":"{{secrets.KAFKA_BOOTSTRAP_SERVERS}}",
    "topicName":"{{secrets.KAFKA_TOPIC_NAME}}"
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

## Element Template

The element templates can be found in the [element-templates/kafka-connector.json](element-templates/kafka-connector.json) file.
