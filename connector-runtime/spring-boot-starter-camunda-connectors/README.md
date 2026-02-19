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

### Overriding Connector Configuration

You can override connector configuration using the Camunda Spring Boot client's native configuration. This is useful when you need to customize connector types for specific use cases.

**YAML configuration example:**

```yaml
camunda:
  client:
    mode: selfmanaged
    worker:
      override:
        "[io.camunda:http-json:1]":
          type: "io.camunda.eaat:http-json:1"
```

**Properties file example:**

```properties
camunda.client.mode=selfmanaged
camunda.client.worker.override.[io.camunda:http-json:1].type=io.camunda.eaat:http-json:1
```

This configuration intercepts jobs with the original type and remaps them to be handled by a worker with a different type. In the example above:
- Jobs with type `io.camunda:http-json:1` are intercepted
- They are remapped to be handled by the worker configured for `io.camunda.eaat:http-json:1`
- This enables gradual connector migration without changing BPMN models

For alternative configuration methods using environment variables, refer to the [Manual Discovery section](../README.md#manual-discovery) in the top-level documentation.
