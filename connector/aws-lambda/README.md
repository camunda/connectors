# Camunda AWS Lambda Connector

Find the user documentation in our [Camunda Platform 8 Docs](https://docs.camunda.io/docs/components/integration-framework/connectors/out-of-the-box-connectors/aws-lambda).

## Build

```bash
mvn clean package
```

## API

### Input

```json
{
  "authentication": {
    "secretKey": "secrets.SECRET_KEY",
    "accessKey": "secrets.ACCESS_KEY",
    "region": "secrets.REGION_KEY"
  },
  "function": {
    "functionName": "secrets.FUNCTION_NAME",
    "operationType": "secrets.OPERATION_TYPE",
    "payload": {
      "event": {
        "data": "value"
      }
    }
  }
}
```

### Output

```json
{
  "result": {
    "statusCode": ".....",
    "executedVersion": ".....",
    "payload": "....."
  }
}
```

## Element Template

The element templates can be found in the [element-templates/aws-lambda-connector.json](../../element-templates/aws-lambda-connector.json) file.
