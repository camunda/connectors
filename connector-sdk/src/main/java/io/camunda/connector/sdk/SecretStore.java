package io.camunda.connector.sdk;

public interface SecretStore {
  String replaceSecret(String value);
}
