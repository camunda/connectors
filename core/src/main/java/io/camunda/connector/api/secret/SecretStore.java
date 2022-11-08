/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.api.secret;

import io.camunda.connector.impl.ConnectorUtil;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Store for environment secrets. It provides a general routine for resolving secrets in String
 * values and how to handle missing secrets when a secret is required in those Strings. Secrets are
 * fetched from the provided {@link SecretProvider}.
 */
public class SecretStore {

  private static final Pattern SECRET_PATTERN_FULL = Pattern.compile("^secrets\\.(?<secret>\\S+)$");
  private static final Pattern SECRET_PATTERN_PLACEHOLDER =
      Pattern.compile("\\{\\{\\s*secrets\\.(?<secret>\\S+?\\s*)}}");

  protected final SecretProvider secretProvider;

  /**
   * Create a store with a specific {@link SecretProvider}.
   *
   * @param secretProvider - providing secret values for secret names
   */
  public SecretStore(SecretProvider secretProvider) {
    this.secretProvider = secretProvider;
  }

  /**
   * Replaces secrets in String values that adhere to the internally defined secrets pattern.
   *
   * @param value - the String to replace secrets in
   * @return the value with replaced secrets
   * @throws IllegalArgumentException if secrets are defined in the value but are not present in the
   *     store
   */
  public String replaceSecret(String value) {
    Optional<String> preparedValue =
        Optional.ofNullable(value).filter(s -> !s.isBlank()).map(String::trim);
    if (preparedValue.isPresent()) {
      Matcher fullMatcher = SECRET_PATTERN_FULL.matcher(preparedValue.get());
      if (fullMatcher.matches()) {
        return getSecret(fullMatcher.group("secret"));
      }
      return replaceSecretPlaceholders(preparedValue.get());
    }
    return value;
  }

  protected String getSecret(String secretName) {
    return Optional.ofNullable(secretProvider.getSecret(secretName))
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    String.format("Secret with name '%s' is not available", secretName)));
  }

  protected String replaceSecretPlaceholders(String original) {
    return ConnectorUtil.replaceTokens(
        original, SECRET_PATTERN_PLACEHOLDER, matcher -> getSecret(matcher.group("secret").trim()));
  }
}
