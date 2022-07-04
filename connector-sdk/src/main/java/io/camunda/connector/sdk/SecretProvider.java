package io.camunda.connector.sdk;

public interface SecretProvider {

  String getSecret(String name);
}
