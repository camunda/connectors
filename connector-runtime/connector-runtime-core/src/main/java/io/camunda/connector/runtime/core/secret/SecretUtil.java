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
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Utility class to replace secrets in strings. */
public class SecretUtil {

  private static final JsonStringEncoder encoder = JsonStringEncoder.getInstance();

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

  /**
   * A denied bracketed reference — e.g. {@code {{secrets.FOO:BAR}}} when only {@code FOO} is
   * allowed — is left untouched by the parentheses pass, but its own text still contains {@code
   * secrets.FOO}, which the bare pattern would happily match and resolve on its own since {@code
   * FOO} genuinely is allowed. Excluding bare matches nested in a still-literal bracketed reference
   * closes that: the bracket pass already ruled on that name, and the bare pass must not
   * re-litigate a truncated prefix of it.
   */
  private static String replaceSecretsWithoutParentheses(
      String input, SecretContext context, SecretReplacer secretReplacer) {
    List<MatchResult> deniedBracketedReferences =
        SECRET_PATTERN_PARENTHESES.matcher(input).results().toList();
    return replaceTokens(
        input,
        SECRET_PATTERN_SECRETS,
        matcher ->
            isNotNestedInAny(matcher, deniedBracketedReferences)
                ? resolveSecretValue(context, secretReplacer, matcher)
                : matcher.group());
  }

  private static String resolveSecretValue(
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

  public static List<String> retrieveSecretKeysInInput(String input) {
    if (Objects.isNull(input)) {
      return List.of();
    }
    List<MatchResult> bracketedReferences =
        SECRET_PATTERN_PARENTHESES.matcher(input).results().toList();
    return Stream.of(SECRET_PATTERN_PARENTHESES, SECRET_PATTERN_SECRETS)
        .flatMap(
            pattern ->
                pattern
                    .matcher(input)
                    .results()
                    .filter(
                        result ->
                            pattern != SECRET_PATTERN_SECRETS
                                || isNotNestedInAny(result, bracketedReferences)))
        .map(matchResult -> matchResult.group("secret"))
        .distinct()
        .toList();
  }

  /**
   * A bare {@code secrets.NAME} match nested inside a {@code {{secrets.NAME}}} occurrence is not a
   * second, independent declaration — it is the bare pattern's narrower character class re-reading
   * the same literal text and stopping short at a character the bracket form tolerates (e.g. {@code
   * :}). Left in, a model declaring only {@code {{secrets.LONG:SUFFIX}}} would also admit the
   * truncated "LONG" into the allow-list, letting a runtime value spell that shorter name and
   * resolve a secret the model never declared.
   */
  private static boolean isNotNestedInAny(MatchResult candidate, List<MatchResult> outerMatches) {
    return outerMatches.stream()
        .noneMatch(outer -> candidate.start() >= outer.start() && candidate.end() <= outer.end());
  }
}
