#  Camunda Amazon Web Services (AWS) Connector
This is a base connector that provides common functionality for working with AWS services. It provides an abstract implementation of `io.camunda.connector.io.camunda.connector.aws.model.AwsService` that can be extended to implement AWS-specific services.

## Service implementation
The AWS Base Connector provides an abstract implementation of `io.camunda.connector.io.camunda.connector.aws.model.AwsService` that can be extended to implement AWS-specific services.

To create a new service implementation, you can follow the steps below:

- Create a new class that extends `io.camunda.connector.io.camunda.connector.aws.model.AwsService`.
- Implement the `invoke` method with your own logic. This method should take in an `AWSStaticCredentialsProvider`, an `AwsBaseConfiguration`, and an `OutboundConnectorContext`, and should return an `Object` that represents the result of the operation.
- Add a private `String` field called `type` to your class.
- Implement the `getType` and `setType` methods to get and set the value of the `type` field.
- Register your new service implementation in the `io.camunda.connector.aws.GsonComponentSupplier` class by adding a new case statement to the `create` method that handles the `type` value of your new service implementation.
- Here's an example implementation of a new service:

```java
public class MyAwsService implements AwsService {

  private String type = "myServiceType";

  @Override
  public Object invoke(
      final AWSStaticCredentialsProvider credentialsProvider,
      final AwsBaseConfiguration configuration,
      final OutboundConnectorContext context) {
    // your AWS-specific service logic goes here
    return null;
  }

  @Override
  public String getType() {
    return type;
  }

  @Override
  public void setType(final String type) {
    this.type = type;
  }
}

```
- Here's an example of registering a new service in GsonComponentSupplier:
```java
public final class GsonComponentSupplier {

  private static final Gson GSON =
      new GsonBuilder()
          .serializeNulls()
          .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
          .registerTypeAdapterFactory(
              RuntimeTypeAdapterFactory.of(AwsService.class, "type")
                  .registerSubtype(AwsDynamoDbService.class, "myServiceType"))
          .create();

  private GsonComponentSupplier() {}

  public static Gson gsonInstance() {
    return GSON;
  }
}
```

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
