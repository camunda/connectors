# Camunda Slack Connector

Find the user documentation in our [Camunda Platform 8 Docs](https://docs.camunda.io/docs/components/integration-framework/connectors/out-of-the-box-connectors/slack/).

## Build

```bash
mvn clean package
```

## API

### Input

```json
{
  "token": ".....",
  "method": "chat.postMessage",
  "data": {
    "channel": "...",
    "text": "..."
  }
}
```

### Output

The response will contain the result of the called Slack method.

```json
{
  "result": {
    "channel": "D03FJ3UJHJM",
      "message": {
        "appId": "A03BBJWJSTD",
        "botId": "B03C1BBGC64",
        "team": "T03BQ73M1UH",
        "text": ":wave: Hi from the slack connector! :partying_face:",
        "ts": "1654636029.472959",
        "type": "message",
        "user": "U03BBJN8B34"
      },
      "ts": "1654636029.472959"
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

The element templates can be found in the [element-templates/slack-connector.json](element-templates/slack-connector.json) file.

## Build a release

Trigger the [release action](./.github/workflows/RELEASE.yml) manually with the version `x.y.z` you want to release and the next SNAPSHOT version.
