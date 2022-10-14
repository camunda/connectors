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
  "myProperty": "....."
}
```

### Output

```json
{
  "result": {
    "myProperty": "....."
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

Trigger the [release action](./.github/workflows/RELEASE.yml) manually with the version `x.y.z` you want to release and the next SNAPSHOT version.
Depending on the release version (major, minor, release candidate), the release artifacts are deployed to the respective Google Cloud Function by another GitHub workflow.
