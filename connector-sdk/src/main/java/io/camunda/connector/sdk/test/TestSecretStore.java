package io.camunda.connector.sdk.test;

import io.camunda.connector.sdk.SecretStore;
import java.util.Map;

public class TestSecretStore extends SecretStore {

  public TestSecretStore(Map<String, String> secrets) {
    super((name) -> secrets.get(name));
  }
}
