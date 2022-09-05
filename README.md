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

## Test locally

Run unit tests

```bash
mvn clean verify
```

### Test as local Job Worker

Use the [Camunda Job Worker Connector Run-Time](https://github.com/camunda/connector-framework/tree/main/runtime-job-worker) to run your function as a local Job Worker.

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

Checkout the repo and branch to build the release from. Run the release script with the version to release and the next
development version. The release script requires git and maven to be setup correctly, and that the user has push rights
to the repository.

The release artifacts are deployed to Google Cloud Function by a GitHub workflow.

```bash
./release.sh 0.3.0 0.4.0
```
