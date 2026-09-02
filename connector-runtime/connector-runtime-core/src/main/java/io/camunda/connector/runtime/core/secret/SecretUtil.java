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
import java.util.Set;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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

  /**
   * A denied bracketed reference — e.g. {@code {{secrets.FOO:BAR}}} when only {@code FOO} is
   * allowed — is left untouched by the parentheses pass, but its own text still contains {@code
   * secrets.FOO}, which the bare pattern would happily match and resolve on its own since {@code
   * FOO} genuinely is allowed. Excluding bare matches nested in a still-literal bracketed reference
   * closes that: the bracket pass already ruled on that name, and the bare pass must not
   * re-litigate a truncated prefix of it. The exclusion is recomputed on every iteration, against
   * whatever {@code input} that iteration is about to scan (positions shift as replacements expand
   * or shrink the text, so a snapshot of positions taken once would go stale), so a still-denied
   * reference stays excluded across the bounded rescan below that lets a resolved value chain into
   * a further replacement.
   *
   * <p>What that per-iteration recompute must not do is treat every bracketed occurrence visible in
   * the current text as denied: a resolution can itself produce new bracketed text (a secret whose
   * stored value happens to spell {@code {{secrets.B}}}), and the parentheses pass never attempted
   * that occurrence at all, so it was never "denied" by anything. {@code deniedBracketedTexts} is
   * the actual denied set, fixed once at the pass boundary from the incoming input; each
   * iteration's recompute is filtered down to it before the nesting check.
   */
  private static String replaceSecretsWithoutParentheses(
      String input, SecretContext context, SecretReplacer secretReplacer) {
    Set<String> deniedBracketedTexts =
        SECRET_PATTERN_PARENTHESES
            .matcher(input)
            .results()
            .map(MatchResult::group)
            .collect(Collectors.toSet());
    var secretVariableNameWithParenthesesMatcher = SECRET_PATTERN_SECRETS.matcher(input);
    while (secretVariableNameWithParenthesesMatcher.find()) {
      List<MatchResult> deniedBracketedReferences =
          SECRET_PATTERN_PARENTHESES
              .matcher(input)
              .results()
              .filter(match -> deniedBracketedTexts.contains(match.group()))
              .toList();
      input =
          replaceTokens(
              input,
              SECRET_PATTERN_SECRETS,
              matcher ->
                  isNotNestedInAny(matcher, deniedBracketedReferences)
                      ? resolveSecretValue(context, secretReplacer, matcher)
                      : matcher.group());
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
    return keysIn(
        input, SECRET_PATTERN_PARENTHESES, SECRET_PATTERN_SECRETS, SecretReferenceUtil.PATTERN);
  }

  /**
   * Every secret name the given text declares in one of the two legacy forms. Excludes the new
   * {@code camunda.secrets.<name>} form, which the legacy providers never resolve, so that a caller
   * asking what the legacy providers were responsible for is not handed names they never held.
   */
  public static List<String> retrieveLegacySecretKeysInInput(String input) {
    return keysIn(input, SECRET_PATTERN_PARENTHESES, SECRET_PATTERN_SECRETS);
  }

  /**
   * Names are trimmed, because that is the name {@link #replaceSecrets} looks up: the parentheses
   * pattern's capture reaches past the name to the closing braces, so {@code <code>{{ secrets.FOO
   * }}</code>} declares {@code FOO}, not {@code "FOO "}. Returning the untrimmed form left every
   * caller comparing a name against one nothing ever resolves — the outbound allow-list did its own
   * trimming, error masking did not, and so masked nothing for a reference written with a space
   * inside the braces.
   */
  private static List<String> keysIn(String input, Pattern... patterns) {
    if (Objects.isNull(input)) {
      return List.of();
    }
    List<MatchResult> bracketedReferences =
        SECRET_PATTERN_PARENTHESES.matcher(input).results().toList();
    return Stream.of(patterns)
        .flatMap(
            pattern ->
                pattern
                    .matcher(input)
                    .results()
                    .filter(
                        result ->
                            pattern == SECRET_PATTERN_PARENTHESES
                                || isNotNestedInAny(result, bracketedReferences)))
        .map(matchResult -> matchResult.group("secret").trim())
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
