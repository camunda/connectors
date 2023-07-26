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

import io.camunda.connector.runtime.core.ConnectorUtil;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Utility class to replace secrets in strings. */
public class SecretUtil {

  private static final Pattern SECRET_PATTERN_SECRETS =
      Pattern.compile("secrets\\.(?<secret>([a-zA-Z0-9]+[\\/._-])*[a-zA-Z0-9]+)");

  private static final Pattern SECRET_PATTERN_PARENTHESES =
      Pattern.compile("\\{\\{\\s*secrets\\.(?<secret>\\S+?\\s*)}}");

  public static String replaceSecrets(String input, Function<String, String> secretReplacer) {
    if (input == null) {
      throw new IllegalStateException("input cant be null.");
    }
    input = replaceSecretsWithParentheses(input, secretReplacer);
    input = replaceSecretsWithoutParentheses(input, secretReplacer);
    return input;
  }

  private static String replaceSecretsWithParentheses(
      String input, Function<String, String> secretReplacer) {
    var secretVariableNameWithParenthesesMatcher = SECRET_PATTERN_PARENTHESES.matcher(input);
    while (secretVariableNameWithParenthesesMatcher.find()) {
      input =
          ConnectorUtil.replaceTokens(
              input,
              SECRET_PATTERN_PARENTHESES,
              matcher -> resolveSecretValue(secretReplacer, matcher));
    }
    return input;
  }

  private static String replaceSecretsWithoutParentheses(
      String input, Function<String, String> secretReplacer) {
    var secretVariableNameWithParenthesesMatcher = SECRET_PATTERN_SECRETS.matcher(input);
    while (secretVariableNameWithParenthesesMatcher.find()) {
      input =
          ConnectorUtil.replaceTokens(
              input,
              SECRET_PATTERN_SECRETS,
              matcher -> resolveSecretValue(secretReplacer, matcher));
    }
    return input;
  }

  private static String resolveSecretValue(
      Function<String, String> secretReplacer, Matcher matcher) {
    var secretName = matcher.group("secret").trim();
    if (!secretName.isBlank() && !secretName.isEmpty()) {
      var result = secretReplacer.apply(secretName);
      if (result != null) {
        return result;
      } else {
        return matcher.group();
      }
    } else {
      return null;
    }
  }
}
