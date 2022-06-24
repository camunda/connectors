package io.camunda.connector.sdk.common;

public interface SecretStore {
  String replaceSecret(String value);
}
