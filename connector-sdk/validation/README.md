# Connector Validation

Default implementation for the [ValdationProvider](../core/src/main/java/io/camunda/connector/api/validation/ValidationProvider.java).

## Example

Adding this module as a dependency makes the [DefaultValidationProvider](./src/main/java/io/camunda/connector/validation/impl/DefaultValidationProvider.java) discoverable via SPI in the SDK core.

Then, you can use [Jakarta Bean Validation Constraints](https://jakarta.ee/specifications/bean-validation/2.0/apidocs/javax/validation/constraints/package-summary.html) on your Connector's input objects.

```java
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;

public class PingRequest {
  @NotEmpty
  private String ping;

  @Min(1)
  @Max(3)
  private Integer someNumber;

  // omitted getters and setter
}
```

An instance of the class can be validated by using the `OutboundConnectorContext.validate` method.

```java
public class PingConnector implements OutboundConnectorFunction {

  @Override
  public Object execute(OutboundConnectorContext context) throws Exception {

    var request = context.getVariablesAsType(PingRequest.class);

    context.validate(request);

    var caller = request.getCaller();

    return new PingResponse("Pong to " + caller);
  }
}
```

## Constraint message interpolation

By default, the validation module uses Hibernate Validator's
[ParameterMessageInterpolator](https://docs.jboss.org/hibernate/validator/6.2/api/org/hibernate/validator/messageinterpolation/ParameterMessageInterpolator.html).
This allows using message parameters in constraint messages, like in the following example:

```java
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;

public class PingRequest {
  @NotEmpty
  private String ping;

  @Min(value = 1, message = "Number must be at least {value}")
  @Max(value = 3, message = "Number must be at most {value}")
  private Integer someNumber;

  // omitted getters and setter
}
```

The validation module does not support using expressions by default as described in the
[Bean Validation API](https://jakarta.ee/specifications/bean-validation/2.0/bean-validation_2.0.html#validationapi-message).
To enable expression support, add a dependency on an expression provider like the following:

```xml
<dependency>
  <groupId>org.glassfish</groupId>
  <artifactId>jakarta.el</artifactId>
  <version>3.0.4</version>
  <scope>test</scope>
</dependency>
```

## Custom validation

You can provide your own validation implementation within your Connector.
Trigger it from within your Connector function as needed and do not use `OutboundConnectorContext.validate`.

## Build

```bash
mvn clean package
```
