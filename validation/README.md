# Connector Validation

Default implementation for the [ValdationProvider](../core/src/main/java/io/camunda/connector/api/validation/ValidationProvider.java).

## Example

Adding this module as a dependency makes the [DefaultValidationProvider](./src/main/java/io/camunda/connector/validation/impl/DefaultValidationProvider.java) discoverable via SPI in the SDK core.

Then, you can use [Jakarta Bean Validation Constraints](https://jakarta.ee/specifications/bean-validation/3.0/apidocs/jakarta/validation/constraints/package-summary.html) on your Connector's input objects.

```java
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;

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
[ParameterMessageInterpolator](https://docs.jboss.org/hibernate/stable/validator/api/org/hibernate/validator/messageinterpolation/ParameterMessageInterpolator.html).
This allows using message parameters in contraint messages, like in the following example:

```java
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;

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
[Bean Validation API](https://jakarta.ee/specifications/bean-validation/3.0/jakarta-bean-validation-spec-3.0.html#validationapi-message).
To enable expression support, add a dependency on an expression provider like the following

```xml
  <dependency>
    <groupId>org.glassfish.expressly</groupId>
    <artifactId>expressly</artifactId>
    <version>5.0.0</version>
  </dependency>
```

## Replace Jakarta Bean Validation implementation

This validation module uses [Hibernate Validator](https://hibernate.org/validator/) to provide an implementation of the Jakarta Bean Validation API.
If you want to provide your own implementation, you can exclude those two from the dependency and add your own.

```xml

<dependencies>
  ...
  <dependency>
    <groupId>io.camunda.connector</groupId>
    <artifactId>connector-validation</artifactId>
    <version>0.2.2</version>
    <exclusions>
      <exclusion>
        <groupId>org.hibernate.validator</groupId>
        <artifactId>hibernate-validator</artifactId>
      </exclusion>
    </exclusions>
  </dependency>

  <dependency>
    <groupId>some.vendor</groupId>
    <artifactId>some-validator-implementation</artifactId>
  </dependency>

  <dependency>
    <groupId>some.vendor</groupId>
    <artifactId>some-el-implementation</artifactId>
  </dependency>
  ...
</dependencies>
```

## Custom validation

If you want to provide your own validation implementation instead of the `connector-validation`, you need to implement the [ValidationProvider](./core/src/main/java/io/camunda/connector/api/validation/ValidationProvider.java) and provide it as an SPI.

```java
public class MyValidationProviderImpl implements ValidationProvider {
  public void validate(Object objectToValidate) {
    // do what you will
    // throw an exception containing your validation result as message if something is wrong
  }
}
```

To provide this as an SPI, create a file `src/main/resources/META-INF/services/io.camunda.connector.api.validation.ValidationProvider` containing the fully qualified classname of your implementation, for example `org.myorg.validation.MyValidationProviderImpl`.

## Build

```bash
mvn clean package
```
