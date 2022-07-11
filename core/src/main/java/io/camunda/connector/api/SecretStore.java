package io.camunda.connector.api;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SecretStore {

  private static final Pattern SECRET_PATTERN = Pattern.compile("^secrets\\.(\\S+)$");

  protected SecretProvider secretProvider;

  public SecretStore(SecretProvider secretProvider) {
    this.secretProvider = secretProvider;
  }

  public String replaceSecret(String value) {
    final Optional<String> secretName =
        Optional.ofNullable(value)
            .map(String::trim)
            .map(SECRET_PATTERN::matcher)
            .filter(Matcher::matches)
            .map(matcher -> matcher.group(1));

    if (secretName.isPresent()) {
      return secretName
          .map(secretProvider::getSecret)
          .orElseThrow(
              () ->
                  new IllegalArgumentException(
                      String.format("Secret with name '%s' is not available", secretName.get())));
    } else {
      return value;
    }
  }
}
