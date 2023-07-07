# Connector Runtime Core utilities

A collection of runtime utilities to wrap Connector functions as job workers and handle Connector discovery.
This artifact can be used to build custom Connector runtimes.
If you are looking for a ready-to-use Connector runtime, refer to the
[Spring Connector Runtime](../connector-runtime-spring) or a corresponding [Spring Boot starter](../spring-boot-starter-camunda-connectors).

## Wrapping a Connector function

Include the job worker runtime utilities as maven dependency:

```xml

<dependency>
  <groupId>io.camunda.connector</groupId>
  <artifactId>connector-runtime-core</artifactId>
  <version>${version.connectors}</version>
</dependency>
```

You can create a job worker by wrapping a Connector function like this:

```java
import io.camunda.connector.slack.outbound.SlackFunction;
import io.camunda.connector.runtime.jobworker.api.outbound.ConnectorJobHandler;
import io.camunda.zeebe.client.ZeebeClient;

public class Main {

  public static void main(String[] args) {

    var zeebeClient = ZeebeClient.newClientBuilder().build();

    zeebeClient.newWorker()
        .jobType("slack")
        .handler(new ConnectorJobHandler(new SlackFunction()))
        .name("SLACK")
        .fetchVariables("foo", "bar")
        .open();
  }
}
```

## Connector discovery

Implementations of the `ConnectorFactory` interface are responsible for Connector
configuration discovery and creation of Connector instances.

You can use out-of-the-box `OutboundConnectorFactory` and`InboundConnectorFactory`
implementations, and you can also extend to support custom discovery mechanisms.
Default discovery mechanism supports configuring Connectors via environment
variables and with SPI. Only one configuration approach must be used per application.

### Discovery via environment variables

Outbound Connector configuration example:
```
CONNECTOR_SLACK_FUNCTION=io.camunda.connector.runtime.util.outbound.SlackFunction
CONNECTOR_SLACK_TYPE=io.camunda.connector:SLACK
CONNECTOR_SLACK_INPUT_VARIABLES=foo,bar
```

Inbound Connector configuration example:
```
CONNECTOR_KAFKA_SUBSCRIPTION_EXECUTABLE=io.camunda.connector.runtime.util.outbound.KafkaSubscription
CONNECTOR_KAFKA_SUBSCRIPTION_TYPE=io.camunda.connector:KAFKA_SUBSCRIPTION
```

When using discovery via environment variables, you can omit the
`@InboundConnector`/`@OutboundConnector` annotation if you provide all the environment variables.
Alternatively, if you decide to keep the annotation, you don't have to provide all the variables.
`CONNECTOR_${NAME}_FUNCTION` for outbound or `CONNECTOR_${NAME}_EXECUTABLE` for inbound will be
enough for discovery to work, and the remaining properties will be populated from annotation.

Connector names must be globally unique, i.e. outbound and inbound Connectors _cannot have
the same name_. If you have both inbound and outbound Connectors for the same system, e.g. RabbitMQ,
we recommend adding a `_SUBSCRIPTION` suffix to the Connector name.

### SPI discovery

SPI discovery utilizes
[Java ServiceProvider Interface](https://docs.oracle.com/javase/tutorial/sound/SPI-intro.html)
to automatically discover Connectors present in classpath. Connector Service Provider interfaces
are `OutboundConnectorFunction` and `InboundConnectorExecutable`.

When using SPI discovery, annotating your connector with `@InboundConnector` or `@OutboundConnector`
is **mandatory**.

## Build

```bash
mvn clean package
```
