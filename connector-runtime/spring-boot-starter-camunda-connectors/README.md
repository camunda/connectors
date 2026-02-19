# Camunda 8 Connectors Spring Boot Starter

This module provides a Spring Boot starter for the Camunda 8 Connector runtime.
The runtime code itself is provided by the [Connector Runtime Spring](../connector-runtime-spring)
module.

## Usage

Refer to the [top-level documentation](../README.md) for the Connector runtime.

## Configuration

The Connector runtime used with this starter can be configured via the following properties:

| Property                             | Description                                                                         | Default |
|--------------------------------------|-------------------------------------------------------------------------------------|---------|
| `camunda.connector.polling.enabled`  | Whether Operate polling is enabled. This is required for inbound Connectors.        | `true`  |
| `camunda.connector.polling.interval` | The interval in which Operate polls for new process deployments.                    | `5000`  |
| `camunda.connector.webhook.enabled`  | Whether webhook connector support is enabled.                                       | `true`  |

### Environment Variables

All configuration properties can also be set via environment variables, following Spring Boot's [relaxed binding rules](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.typesafe-configuration-properties.relaxed-binding.environment-variables).
Property names are converted by replacing dots (`.`) and dashes (`-`) with underscores (`_`) and converting to uppercase.

**Examples:**
- `camunda.connector.polling.enabled` → `CAMUNDA_CONNECTOR_POLLING_ENABLED`
- `camunda.connector.polling.interval` → `CAMUNDA_CONNECTOR_POLLING_INTERVAL`
- `camunda.connector.webhook.enabled` → `CAMUNDA_CONNECTOR_WEBHOOK_ENABLED`

This is especially useful when running the connector runtime in containerized environments like Docker or Kubernetes:

```bash
docker run -e CAMUNDA_CONNECTOR_POLLING_ENABLED=true \
           -e CAMUNDA_CONNECTOR_POLLING_INTERVAL=10000 \
           my-connector-app:latest
```
