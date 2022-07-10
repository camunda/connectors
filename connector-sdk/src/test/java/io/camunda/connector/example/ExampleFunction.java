package io.camunda.connector.example;

import io.camunda.connector.api.ConnectorContext;
import io.camunda.connector.api.ConnectorFunction;
import io.camunda.connector.api.Validator;

public class ExampleFunction implements ConnectorFunction {

  @Override
  public Object execute(ConnectorContext context) throws Exception {

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
