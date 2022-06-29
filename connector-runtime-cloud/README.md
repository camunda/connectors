# Cloud Connector Run-Time

Run-time for [connector functions](../connector-sdk) based on [Google Cloud Functions](https://github.com/GoogleCloudPlatform/functions-framework-java), integrated with Camunda Cloud via the [Connector Bridge](https://github.com/camunda/cloud-connector-bridge).

## Usage

Put your connector function on the classpath and use `io.camunda.connectors.cloud.CloudConnectorFunction` as the [GCP function](https://github.com/GoogleCloudPlatform/functions-framework-java):

```bash
java -jar java-function-invoker-1.1.0 \
    --classpath ... \
    --target io.camunda.connectors.cloud.CloudConnectorFunction
```

If you don't want your connector to auto-magically load, configure it via the `CONNECTOR_FUNCTION` environment variable.

## Build

```bash
mvn clean package
```
