# Connector Core

The foundation for re-usable connector functionality.

## Example

A connector implements [`OutboundConnectorFunction#execute(OutboundConnectorContext)`](./src/main/java/io/camunda/connector/api/outbound/OutboundConnectorFunction.java) to define the connector logic.

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

Connector run-times, e.g. [job worker run-time](../runtime) wrap the function to execute it in various environments.


## Build

```bash
mvn clean package
```
