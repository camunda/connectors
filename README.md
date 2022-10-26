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
    "secretKey":"mySecretKey",
    "accessKey":"myAccessKey"
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

## Test locally

Run unit tests

```bash
mvn clean verify
```

### Test as local Job Worker

Use
the [Camunda Job Worker Connector Run-Time](https://github.com/camunda/connector-framework/tree/main/runtime-job-worker)
to run your function as a local Job Worker.

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

The element templates can be found in
the [element-templates/aws-sns-connector.json](element-templates/aws-sns-connector.json) file.

## Build a release

Trigger the [release action](./.github/workflows/RELEASE.yml) manually with the version `x.y.z` you want to release and the next SNAPSHOT version.
Depending on the release version (major, minor, release candidate), the release artifacts are deployed to the respective Google Cloud Function by another GitHub workflow.
