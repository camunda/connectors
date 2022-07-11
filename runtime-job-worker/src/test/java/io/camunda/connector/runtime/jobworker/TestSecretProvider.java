package io.camunda.connector.runtime.jobworker;

import io.camunda.connector.api.SecretProvider;
import java.util.Collections;
import java.util.Map;

public class TestSecretProvider implements SecretProvider {

  public static final String SECRET_NAME = "FOO";
  public static final String SECRET_VALUE = "bar";

  private static final Map<String, String> SECRETS =
      Collections.singletonMap(SECRET_NAME, SECRET_VALUE);

  @Override
  public String getSecret(String value) {
    System.out.println("REPLACE " + value);
    return SECRETS.get(value);
  }
}
