# Connector SDK

The foundation for re-usable connector functionality.
Wrapping is possible for individual application scenarios ([Camunda cloud](../connector-runtime-cloud), [job worker](../connector-runtime-job-worker)).

## Example

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

## Build

```bash
mvn clean package
```
