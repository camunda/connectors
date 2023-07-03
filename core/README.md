# Connector Core

The foundation for re-usable Connector functionality.

## Example

### Outbound Connector

An outbound Connector implements [`OutboundConnectorFunction#execute(OutboundConnectorContext)`](./src/main/java/io/camunda/connector/api/outbound/OutboundConnectorFunction.java) to define the connector logic.

```java

@OutboundConnector(
    name = "PING",
    inputVariables = {"caller"},
    type = "io.camunda.example.PingConnector:1"
)
public class PingConnector implements OutboundConnectorFunction {

  @Override
  public Object execute(OutboundConnectorContext context) throws Exception {
    var request = context.bindVariables(PingRequest.class);
    var caller = request.getCaller();
    return new PingResponse("Pong to " + caller);
  }
}
```

### Inbound Connector

An inbound Connector implements [`InboundConnectorExecutable#activate(InboundConnectorContext)`](./src/main/java/io/camunda/connector/api/inbound/InboundConnectorExecutable.java) and [`InboundConnectorExecutable#deactivate()`](./src/main/java/io/camunda/connector/api/inbound/InboundConnectorExecutable.java) to define the connector logic.

```java
@InboundConnector(
    name = "SUBSCRIPTION",
    type = "io.camunda.example.SubscriptionConnector:1"
)
public class SubscriptionConnector implements InboundConnectorExecutable {

  private MockSubscription subscription; // imitates some real-world subscription

  @Override
  public void activate(InboundConnectorContext context) throws Exception {

    var properties = context.getPropertiesAsType(SubscriptionProperties.class);

    context.replaceSecrets(properties);
    context.validate(properties);

    // subscribe to events
    subscription = new MockSubscription(properties.getTopic());
    subscription.subscribe(event -> {
      context.correlate(event);
    });
  }

  @Override
  public void deactivate() throws Exception {
    // unsubscribe from events
    subscription.shutdown();
  }
}
```

### Connector discovery

Connectors expose themselves as a [`OutboundConnectorFunction` or `InboundConnectorExecutable` SPI implementations](https://docs.oracle.com/javase/8/docs/api/java/util/ServiceLoader.html).

Connector runtimes like [Spring Zeebe](https://github.com/camunda-community-hub/spring-zeebe#run-outboundconnectors) wrap the function to execute it in various environments.


## Build

```bash
mvn clean package
```
