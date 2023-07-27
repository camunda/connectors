# Camunda AWS SNS Connector

Find the user documentation in our [Camunda Platform 8 Docs](https://docs.camunda.io/docs/components/integration-framework/connectors/out-of-the-box-connectors/aws-sns/).

## Build

```bash
mvn clean package
```

## API

### Input

```json
{
  "authentication":{
    "accessKey":"{{secrets.SNS_ACCESS_KEY}}",
    "secretKey":"{{secrets.SNS_SECRET_KEY}}"
  },
  "topic":{
    "message":"MyMessage",
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
    "subject":"MySubject",
    "region":"us-east-1",
    "topicArn":"arn:aws:sns:us-east-1:00000000:MySNSTopic"
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
the [element-templates/aws-sns-connector.json](element-templates/aws-sns-connector.json) file.
