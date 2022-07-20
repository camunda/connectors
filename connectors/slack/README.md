# cloud-connector-slack

Camunda Cloud Slack Connector

## Build

```bash
mvn clean package
```

## API

### Input

```json
{
  "clusterId": "test",
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

### Test as local Google Cloud Function

Build as Google Cloud Function

```bash
mvn function:run -Pcloud-function
```

The function will be available at http://localhost:9082.

Have a look at the [Camunda Cloud Connector Run-Time](https://github.com/camunda/connector-runtime-cloud) to see how your Connector function is wrapped as a Google Cloud Function.

### Test as local Job Worker

Use the [Camunda Job Worker Connector Run-Time](https://github.com/camunda/connector-framework/tree/main/runtime-job-worker) to run your function as a local Job Worker.

## Element Template

The element templates can be found in the [element-templates/slack-connector.json](element-templates/slack-connector.json) file.

## Build a release

Checkout the repo and branch to build the release from. Run the release script with the version to release and the next
development version. The release script requires git and maven to be setup correctly, and that the user has push rights
to the repository.

The release artifacts are deployed to Google Cloud Function by a GitHub workflow.

```bash
./release.sh 0.3.0 0.4.0
```
