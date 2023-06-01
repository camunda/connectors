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
| `operate.client.enabled`             | Whether default Operate client is enabled. This is required for inbound Connectors. | `true`  |
