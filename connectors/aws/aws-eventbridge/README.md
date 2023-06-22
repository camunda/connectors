# Camunda AWS EventBridge Connector

Find the user documentation in our [Camunda Platform 8 Docs](https://docs.camunda.io/docs/components/integration-framework/connectors/out-of-the-box-connectors/aws-eventbridge).

## Build
```bash
mvn clean package
```

## API

### Input
```json
{
    "authentication": {
      "secretKey": "my-aws-secret-key",
      "accessKey": "my-aws-access-key"
    },
    "configuration": {
      "region": "ua-east-1"
    },
    "input": {
      "detailType": "detail-type",
      "eventBusName": "event-bus-name",
      "source": "event-source",
      "detail": {"key1": {"innerKey": "innerValue"}}
    }
  }
```

### Output
```json
{
  "sdkResponseMetadata": {
    "requestId": "929bf054-193b-48e6-ab80-3aeeb613b415"
  },
  "sdkHttpMetadata": {
    "httpHeaders": {
      "Content-Length": "85",
      "Content-Type": "application/x-amz-json-1.1",
      "Date": "Thu, 22 Jun 2023 12:07:47 GMT",
      "x-amzn-RequestId": "929bf054-193b-48e6-ab80-3aeeb613b415"
    },
    "httpStatusCode": 200,
    "allHttpHeaders": {
      "x-amzn-RequestId": [
        "929bf054-193b-48e6-ab80-3aeeb613b415"
      ],
      "Content-Length": [
        "85"
      ],
      "Date": [
        "Thu, 22 Jun 2023 12:07:47 GMT"
      ],
      "Content-Type": [
        "application/x-amz-json-1.1"
      ]
    }
  },
  "failedEntryCount": 0,
  "entries": [
    {
      "eventId": "0dcd8361-d287-b9eb-bbb2-a2512851c2bf",
      "errorCode": null,
      "errorMessage": null
    }
  ]
}
```

## Element Template

The element templates can be found in the [element-templates/aws-eventbridge-connector.json](element-templates/aws-eventbridge-connector.json) file.
