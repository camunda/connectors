package io.camunda.connector.sdk.test;

import io.camunda.connector.sdk.ConnectorContext;
import io.camunda.connector.sdk.SecretProvider;
import io.camunda.connector.sdk.SecretStore;
import java.util.HashMap;
import java.util.Map;

public class ConnectorContextBuilder {

  protected Map<String, String> secrets = new HashMap<>();
  protected SecretProvider secretProvider = (name) -> secrets.get(name);

  protected String variablesAsJSON;

  private Object variablesAsObject;

  public static ConnectorContextBuilder create() {
    return new ConnectorContextBuilder();
  }

  private void assertNoVariables() {

    if (this.variablesAsJSON != null) {
      throw new IllegalStateException("variablesAsJSON already set");
    }

    if (this.variablesAsObject != null) {
      throw new IllegalStateException("variablesAsObject already set");
    }
  }

  /**
   * Provides the variables as a JSON string.
   *
   * @param variablesAsJSON
   */
  public ConnectorContextBuilder variables(String variablesAsJSON) {
    this.assertNoVariables();

    this.variablesAsJSON = variablesAsJSON;
    return this;
  }

  /**
   * Provides the variables as an object
   *
   * @param variablesAsObject
   */
  public ConnectorContextBuilder variables(Object variablesAsObject) {
    this.assertNoVariables();

    this.variablesAsObject = variablesAsObject;
    return this;
  }

  public ConnectorContextBuilder secret(String name, String value) {
    secrets.put(name, value);
    return this;
  }

  public ConnectorContextBuilder secrets(SecretProvider secretProvider) {
    this.secretProvider = secretProvider;
    return this;
  }

  public ConnectorContext build() {
    return new ConnectorContext(){

      @Override
      public String getVariables() {

        if (variablesAsJSON == null) {
          throw new IllegalStateException("variablesAsJSON not provided");
        }

        return variablesAsJSON;
      }

      @Override
      public <T extends Object> T getVariablesAsType(Class<T> cls) {

        if (variablesAsObject == null) {
          throw new IllegalStateException("variablesAsObject not provided");
        }

        try {
          return cls.cast(variablesAsObject);
        } catch (ClassCastException ex) {
          throw new IllegalStateException("no variablesAsObject of type " + cls.getName() + " provided", ex);
        }
      }

      @Override
      public SecretStore getSecretStore() {
        return new SecretStore(secretProvider);
      }
    };
  }
}
