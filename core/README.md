# Connector Core

The foundation for re-usable Connector functionality.

## Example

A Connector implements [`OutboundConnectorFunction#execute(OutboundConnectorContext)`](./src/main/java/io/camunda/connector/api/outbound/OutboundConnectorFunction.java) to define the connector logic.

```java

@OutboundConnector(
    name = "PING",
    inputVariables = {"caller"},
    type = "io.camunda.example.PingConnector:1"
)
public class PingConnector implements OutboundConnectorFunction {

  @Override
  public Object execute(OutboundConnectorContext context) throws Exception {

    var request = context.getVariablesAsType(PingRequest.class);

    context.replaceSecrets(request);

    var caller = request.getCaller();

    return new PingResponse("Pong to " + caller);
  }
}
```

It exposes itself as a [`OutboundConnectorFunction` SPI implementation](https://docs.oracle.com/javase/8/docs/api/java/util/ServiceLoader.html).

Connector runtimes like [Spring Zeebe](https://github.com/camunda-community-hub/spring-zeebe#run-outboundconnectors) wrap the function to execute it in various environments.


## Build

```bash
mvn clean package
```
