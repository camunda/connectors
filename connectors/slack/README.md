# cloud-connector-slack

Camunda Cloud Slack Connector

## Build

```bash
mvn clean package
```

## API

### Request

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

### Response

The response will contain the status code, the headers and the body of the response of the HTTP service.

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

```bash
mvn compile function:run
```

The function will be available under `http://localhost:9082`.

### Local secrets

To inject secrets during execution export a `CONNECTOR_SECRETS` environment variable

```bash
export CONNECTOR_SECRETS='{...}'
```

And reference the secret in the request payload prefixed with `secrets.MY_SECRET`.

### Send a request

Save the request in a file, i.e. `request.json` and use curl to invoke the function.

```bash
curl -X POST -H "Content-Type: application/json" -d @request.json http://localhost:9082
```

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
