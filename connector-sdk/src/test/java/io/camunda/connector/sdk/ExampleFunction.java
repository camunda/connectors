package io.camunda.connector.sdk;

public class ExampleFunction implements ConnectorFunction {

  @Override
  public Object execute(ConnectorContext context) {

    var input = context.getVariablesAsType(ExampleInput.class);

    final var validator = new Validator();
    input.validate(validator);
    validator.validate();

    input.replaceSecrets(context.getSecretStore());

    var foo = input.getFoo();

    if (foo.equals("BOOM!")) {
      throw new UnsupportedOperationException("expected BOOM!");
    }

    return foo;
  }
}
