# Spring Runtime for Camunda 8 Connectors

This module provides a Spring-based implementation for an embedded Connector Runtime on top of the [Connector Runtime Core](../connector-runtime-core) module.

**Note**: the recommended way to run the Connector Runtime in a Spring Boot application is to use the [Spring Boot starter](../spring-boot-starter-camunda-connectors).

## Usage

To add the embeddable Connector runtime to your Spring Boot project, add the following dependency to your Spring project:

```xml
<dependency>
  <groupId>io.camunda.connector</groupId>
  <artifactId>connector-runtime-spring</artifactId>
  <version>${version.connectors}</version>
</dependency>
```

Refer to the [Releases](https://github.com/camunda/connectors-bundle/releases) page for the latest version.

## Spring Configuration

The following Spring `@Configuration` classes are available to configure the Connector Runtime:
* [`OutboundConnectorRuntimeConfiguration`](src/main/java/io/camunda/connector/runtime/outbound/OutboundConnectorRuntimeConfiguration.java) - for outbound Connectors
* [`InboundConnectorRuntimeConfiguration`](src/main/java/io/camunda/connector/runtime/inbound/InboundConnectorRuntimeConfiguration.java) - for inbound Connectors
* [`WebhookConnectorRuntimeConfiguration`](src/main/java/io/camunda/connector/runtime/inbound/WebhookConnectorConfiguration.java) - for inbound webhook Connectors. This enables the webhook REST endpoint.
