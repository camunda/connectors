package io.camunda.connector.api;

public interface SecretProvider {

  String getSecret(String name);
}
