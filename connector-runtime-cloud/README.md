# connector-runtime-cloud

Run-time for [connector functions](../connector-sdk) based
on [Google Cloud Functions](https://github.com/GoogleCloudPlatform/functions-framework-java), integrated with Camunda
Cloud via the [Connector Bridge](https://github.com/camunda/cloud-connector-bridge).

## Usage

Specify `io.camunda.connectors.cloud.CloudConnectorFunction` as
the [GCP function](https://github.com/GoogleCloudPlatform/functions-framework-java) and define the connector function
via the `CONNECTOR_FUNCTION` environment variable:

```bash
CONNECTOR_FUNCTION=io.camunda.connectors.slack.SlackFunction

java -jar java-function-invoker-1.1.0 \
    --classpath ... \
    --target io.camunda.connectors.cloud.CloudConnectorFunction
```

## Build

```bash
mvn clean package
```
