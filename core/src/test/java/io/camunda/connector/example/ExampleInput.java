package io.camunda.connector.example;

import io.camunda.connector.api.SecretStore;
import io.camunda.connector.api.Validator;

public class ExampleInput {

  private String foo;

  public ExampleInput() {}

  public ExampleInput(String foo) {
    this.foo = foo;
  }

  public void validateWith(Validator validator) {
    validator.require(foo, "Test - foo");
  }

  public void replaceSecrets(SecretStore secretStore) {
    this.foo = secretStore.replaceSecret(this.foo);
  }

  public String getFoo() {
    return foo;
  }

  public void setFoo(String foo) {
    this.foo = foo;
  }
}
