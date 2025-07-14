package io.camunda.connector.test;

import io.camunda.connector.api.secret.SecretContext;
import io.camunda.connector.api.secret.SecretProvider;
import java.util.Map;

public class MapSecretProvider implements SecretProvider {
  private final Map<String, String> secrets;

  public MapSecretProvider(Map<String, String> secrets) {
    this.secrets = secrets;
  }

  @Override
  public String getSecret(String name, SecretContext context) {
    return secrets.get(name);
  }
}
