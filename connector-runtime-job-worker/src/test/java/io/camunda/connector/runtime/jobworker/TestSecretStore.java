package io.camunda.connector.runtime.jobworker;

import io.camunda.connector.sdk.SecretStore;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

public class TestSecretStore implements SecretStore {

  public static final String SECRET_NAME = "foo";
  public static final String SECRET_VALUE = "bar";

  private static final Map<String, String> SECRETS =
      Collections.singletonMap(SECRET_NAME, SECRET_VALUE);

  @Override
  public String replaceSecret(String value) {
    System.out.println("REPLACE " + value);
    return Optional.ofNullable(SECRETS.get(value)).orElse(value);
  }
}
