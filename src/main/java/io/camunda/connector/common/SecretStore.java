package io.camunda.connector.common;

public interface SecretStore {
  String replaceSecret(String value);
}
