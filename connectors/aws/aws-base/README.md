#  Camunda Amazon Web Services (AWS) Connector
This is a base connector that provides common functionality for working with AWS services. It provides an abstract implementation of `io.camunda.connector.io.camunda.connector.aws.model.AwsService` that can be extended to implement AWS-specific services.

## Service implementation

 - For implementing AWS service connector template, you need to use the following task definition type: io.camunda:aws:1. Here's an example of an AWS service connector template that uses AWS Base Connector as the entry point:
```json
{
  "$schema": "https://unpkg.com/@camunda/zeebe-element-templates-json-schema/resources/schema.json",
  "name": "AWS myServiceType",
  "id": "io.camunda.connectors.myService.v1",
  "documentationRef": "https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/aws-myService/",
  "category": {
    "id": "connectors",
    "name": "Connectors"
  },
  "appliesTo": [
    "bpmn:Task"
  ],
  "elementType": {
    "value": "bpmn:ServiceTask"
  },
  "groups": [
  ],
  "properties": [
    {
      "type": "Hidden",
      "value": "io.camunda:aws:1",
      "binding": {
        "type": "zeebe:taskDefinition:type"
      }
    },
    {
      "type": "Hidden",
      "value": "myServiceType",
      "binding": {
        "type": "zeebe:input",
        "name": "service.type"
      },
      "constraints": {
        "notEmpty": true
      }
    }
  ]
}
```


## Implemented services :
### AWS DynamoDB Connector
The [AWS DynamoDB Connector](https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/aws-dynamodb/) allows you to connect your BPMN service with [Amazon Web Service's DynamoDB Service](https://aws.amazon.com/dynamodb/). This can be useful for performing CRUD operations on AWS DynamoDB tables from within a BPMN process.
### AWS EventBridge Connector
The [AWS EventBridge Connector](https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/aws-eventbridge/) allows you to send events from your BPMN service to [Amazon Web Service's EventBridge Service](https://aws.amazon.com/eventbridge/).
