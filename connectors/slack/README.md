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

## Element Template

The element templates can be found in the [element-templates/slack-connector.json](element-templates/slack-connector.json) file.
