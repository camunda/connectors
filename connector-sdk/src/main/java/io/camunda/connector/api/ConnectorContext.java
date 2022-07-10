package io.camunda.connector.api;

public interface ConnectorContext {

  String getVariables();

  <T extends Object> T getVariablesAsType(Class<T> cls);

  SecretStore getSecretStore();
}
