# Connector SDK

The foundation for re-usable connector functionality.

## Example

A connector implements [`ConnectorFunction#execute(ConnectorContext)`](https://github.com/camunda/connectors-framework/blob/main/connector-sdk/src/main/java/io/camunda/connector/sdk/ConnectorFunction.java) to define the connector logic.

```java
public class PingConnector implements ConnectorFunction {

  @Override
  public Object execute(ConnectorContext context) {

    final var request = context.getVariablesAsType(PingRequest.class);

    final var validator = new Validator();
    request.validate(validator);
    validator.validate();

    request.replaceSecrets(context.getSecretStore());

    try {
      var name = request.getCaller();

      return new PingResponse("Pong to " + caller);
    } catch (final Exception e) {
      throw ConnectorResponse.failed(e);
    }
  }
}
```

It exposes itself as a [`ConnectorFunction` SPI implementation](https://docs.oracle.com/javase/8/docs/api/java/util/ServiceLoader.html).

Connector run-times, e.g. [job worker run-time](../connector-runtime-job-worker) wrap the function to execute it in various environments.


## Build

```bash
mvn clean package
```
