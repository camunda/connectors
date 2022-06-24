package io.camunda.connector.sdk.common;

public interface ConnectorContext {
  <T extends Object> T getVariablesAsType(Class<T> cls);

  SecretStore getSecretStore();
}
