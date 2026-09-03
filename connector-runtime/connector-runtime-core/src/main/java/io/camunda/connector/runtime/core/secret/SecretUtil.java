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
package io.camunda.connector.runtime.core.secret;

import io.camunda.connector.api.secret.SecretContext;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Utility class to replace secrets in strings. */
public class SecretUtil {

  private static final Pattern SECRET_PATTERN_SECRETS =
      Pattern.compile("secrets\\.(?<secret>([a-zA-Z0-9]+[\\/._-])*[a-zA-Z0-9]+)");

  private static final Pattern SECRET_PATTERN_PARENTHESES =
      Pattern.compile("\\{\\{\\s*secrets\\.(?<secret>\\S+?\\s*)}}");

  public static String replaceSecrets(
      String input, SecretContext context, SecretReplacer secretReplacer) {
    if (input == null) {
      throw new IllegalStateException("input cant be null.");
    }
    input = replaceSecretsWithParentheses(input, context, secretReplacer);
    input = replaceSecretsWithoutParentheses(input, context, secretReplacer);
    return input;
  }

  private static String replaceSecretsWithParentheses(
      String input, SecretContext context, SecretReplacer secretReplacer) {
    var secretVariableNameWithParenthesesMatcher = SECRET_PATTERN_PARENTHESES.matcher(input);
    while (secretVariableNameWithParenthesesMatcher.find()) {
      input =
          replaceTokens(
              input,
              SECRET_PATTERN_PARENTHESES,
              matcher -> resolveSecretValue(context, secretReplacer, matcher));
    }
    return input;
  }

  private static String replaceSecretsWithoutParentheses(
      String input, SecretContext context, SecretReplacer secretReplacer) {
    var secretVariableNameWithParenthesesMatcher = SECRET_PATTERN_SECRETS.matcher(input);
    while (secretVariableNameWithParenthesesMatcher.find()) {
      input =
          replaceTokens(
              input,
              SECRET_PATTERN_SECRETS,
              matcher -> resolveSecretValue(context, secretReplacer, matcher));
    }
    return input;
  }

  private static String resolveSecretValue(
      SecretContext context, SecretReplacer secretReplacer, Matcher matcher) {
    var secretName = matcher.group("secret").trim();
    if (!secretName.isBlank()) {
      var result = secretReplacer.replaceSecrets(secretName, context);
      if (result != null) {
        return result;
      } else {
        return matcher.group();
      }
    } else {
      return null;
    }
  }

  public static String replaceTokens(
      String original, Pattern pattern, Function<Matcher, String> converter) {
    int lastIndex = 0;
    StringBuilder output = new StringBuilder();
    Matcher matcher = pattern.matcher(original);
    while (matcher.find()) {
      output.append(original, lastIndex, matcher.start()).append(converter.apply(matcher));
      lastIndex = matcher.end();
    }
    if (lastIndex < original.length()) {
      output.append(original, lastIndex, original.length());
    }
    return output.toString();
  }

  /**
   * Names are trimmed, because that is the name {@link #resolveSecretValue} looks up: the
   * parentheses pattern's capture reaches past the name to the closing braces, so {@code {{
   * secrets.FOO }}} declares {@code FOO}, not {@code "FOO "}. Returning the untrimmed form denies a
   * legitimately declared name at resolution time -- for a name outside the bare pattern's
   * character class (e.g. {@code FOO:BAR}), there is no independent bare-pattern match to
   * coincidentally supply the trimmed form, so the declared secret is silently left unresolved
   * under STRICT.
   */
  public static List<String> retrieveSecretKeysInInput(String input) {
    return Objects.isNull(input)
        ? List.of()
        : Stream.of(SECRET_PATTERN_PARENTHESES, SECRET_PATTERN_SECRETS)
            .map(pattern -> pattern.matcher(input))
            .flatMap(Matcher::results)
            .map(matchResult -> matchResult.group("secret").trim())
            .distinct()
            .toList();
  }
}
