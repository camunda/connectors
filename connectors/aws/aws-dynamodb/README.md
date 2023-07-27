# Camunda AWS SNS Connector

Find the user documentation in
our [Camunda Platform 8 Docs](https://docs.camunda.io/docs/components/integration-framework/connectors/out-of-the-box-connectors/aws-dynamodb/)
.

## Build

```bash
mvn clean package
```

## API

### Input

```json
{
  "authentication": {
    "accessKey": "{{secrets.SNS_ACCESS_KEY}}",
    "secretKey": "{{secrets.SNS_SECRET_KEY}}"
  },
  "input": {
    "type": "getItem",
    "tableName": "{{secrets.TABLE_NAME_KEY}}",
    "primaryKeyComponents": {
      "id": "{{secrets.KEY_ATTRIBUTE_VALUE}}"
    }
  }
}
```

## Element Template

The element templates can be found in the [element-templates/aws-dynamodb-outbound-connector.json](element-templates/aws-dynamodb-connector.json)file.
