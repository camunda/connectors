package io.camunda.connector.common;

public interface ConnectorContext {
  <T extends Object> T getVariableAsType(Class<T> cls);

  SecretStore getSecretStore();
}
