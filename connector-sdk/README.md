# Connector SDK

The foundation for re-usable connector functionality.
Wrapping is possible for individual application scenarios ([Camunda cloud](../connector-runtime-cloud), [job worker](../connector-runtime-job-worker)).

## Example

```java
public class MyConnectorFunction implements ConnectorFunction {
  private static final Logger LOGGER
      = LoggerFactory.getLogger(MyConnectorFunction.class);

  @Override
  public Object execute(ConnectorContext context) {

    final var request = context.getVariablesAsType(MyConnectorRequest.class);

    final var validator = new Validator();
    request.validate(validator);
    validator.validate();

    request.replaceSecrets(context.getSecretStore());

    try {
      var name = request.getCaller();

      return new MyConnectorResponse("Pong to " + caller);
    } catch (final Exception e) {
      LOGGER.error("Failed to execute request: " + e.getMessage(), e);

      throw ConnectorResponse.failed(e);
    }
  }
}
```

## Build

```bash
mvn clean package
```
