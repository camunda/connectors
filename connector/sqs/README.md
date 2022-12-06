# Camunda AWS SQS Connector

Find the user documentation in our [Camunda Platform 8 Docs](https://docs.camunda.io/docs/components/integration-framework/connectors/out-of-the-box-connectors/aws-sqs/).

## Build

```bash
mvn clean package
```

## API

### Input

```json
{
  "authentication":{
    "secretKey":"secrets.AWS_SECRET_KEY",
    "accessKey":"secrets.AWS_ACCESS_KEY"
  },
  "queue":{
    "messageAttributes":{
      "attribute2":{
        "StringValue":"attribute 2 value",
        "DataType":"String"
      },
      "attribute1":{
        "StringValue":"attribute 1 value",
        "DataType":"String"
      }
    },
    "messageBody":{
      "data":"ok"
    },
    "region":"us-east-1",
    "url":"https://sqs.us-east-1.amazonaws.com/000000000/my-sqs"
  }
}
```

### Output

```json
{
  "result": {
    "messageId": "c158a652-c3e3-5511-a565-fd01a05c0c45"
  }
}
```

## Element Template

The element templates can be found in
the [element-templates/aws-sqs-connector.json](element-templates/aws-sqs-connector.json) file.
