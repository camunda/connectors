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

import com.fasterxml.jackson.core.io.JsonStringEncoder;
import io.camunda.connector.api.secret.SecretContext;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/** Utility class to replace secrets in strings. */
public class SecretUtil {

  private static final JsonStringEncoder encoder = JsonStringEncoder.getInstance();

  // The negative lookbehind keeps this legacy pattern out of a new-form camunda.secrets.<name>
  // reference, which ends in the same three characters. Without it the legacy pass, which runs
  // first and over raw model text, would replace secrets.TOKEN inside camunda.secrets.TOKEN from a
  // local provider — reading the wrong store, and destroying the reference before the cluster ever
  // sees it.
  private static final Pattern SECRET_PATTERN_SECRETS =
      Pattern.compile("(?<!camunda\\.)secrets\\.(?<secret>([a-zA-Z0-9]+[\\/._-])*[a-zA-Z0-9]+)");

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

  private static @Nullable String resolveSecretValue(
      SecretContext context, SecretReplacer secretReplacer, Matcher matcher) {
    var secretName = matcher.group("secret").trim();
    if (!secretName.isBlank()) {
      var result = secretReplacer.replaceSecrets(secretName, context);
      if (result != null) {
        return new String(encoder.quoteAsString(result));
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
   * Whether the text contains a legacy secret reference, in either of its two spellings. Used where
   * the legacy form is not supported and has to be reported rather than silently left in place.
   */
  public static boolean containsLegacySecretReference(String input) {
    return input != null
        && (SECRET_PATTERN_PARENTHESES.matcher(input).find()
            || SECRET_PATTERN_SECRETS.matcher(input).find());
  }

  /**
   * Every secret name the given text declares, in either form. The new form is included so that
   * excluding it from {@link #SECRET_PATTERN_SECRETS} does not shrink the outbound allow-list this
   * feeds: a name a model declares as {@code camunda.secrets.NAME} stays permitted, exactly as it
   * was before the two patterns were separated.
   */
  public static List<String> retrieveSecretKeysInInput(String input) {
    return Objects.isNull(input)
        ? List.of()
        : Stream.of(SECRET_PATTERN_PARENTHESES, SECRET_PATTERN_SECRETS, SecretReferenceUtil.PATTERN)
            .map(pattern -> pattern.matcher(input))
            .flatMap(Matcher::results)
            .map(matchResult -> matchResult.group("secret"))
            .distinct()
            .toList();
  }
}
