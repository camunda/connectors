# Spring Runtime for Camunda 8 Connectors

This module provides a Spring configuration for an embedded Connector Runtime.

## Usage

Add the following dependency to your Spring project:

```xml
<dependency>
  <groupId>io.camunda</groupId>
  <artifactId>spring-boot-starter-camunda-connectors</artifactId>
  <version>${version.spring-zeebe}</version>
</dependency>
```

To run the Connector Runtime, you can either use the `ConnectorRuntimeAutoConfiguration` or the `ConnectorRuntime` directly.
