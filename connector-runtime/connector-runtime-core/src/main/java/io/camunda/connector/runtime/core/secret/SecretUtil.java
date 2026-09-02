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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.camunda.connector.api.secret.SecretContext;
import io.camunda.connector.runtime.core.secret.SecretFilter.Secret;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/** Utility class to replace secrets in strings. */
public class SecretUtil {

  // The negative lookbehind keeps this legacy pattern out of a new-form camunda.secrets.<name>
  // reference, which ends in the same three characters. Without it the legacy pass, which runs
  // first and over raw model text, would replace secrets.TOKEN inside camunda.secrets.TOKEN from a
  // local provider — reading the wrong store, and destroying the reference before the cluster ever
  // sees it.
  private static final Pattern SECRET_PATTERN_SECRETS =
      Pattern.compile("(?<!camunda\\.)secrets\\.(?<secret>([a-zA-Z0-9]+[\\/._-])*[a-zA-Z0-9]+)");

  private static final Pattern SECRET_PATTERN_PARENTHESES =
      Pattern.compile("\\{\\{\\s*secrets\\.(?<secret>\\S+?\\s*)}}");

  public static JsonNode replaceSecrets(
      JsonNode input, SecretContext context, SecretReplacer secretReplacer) {
    if (input == null) {
      throw new IllegalStateException("input cant be null.");
    }
    if (!input.isObject()) {
      throw new IllegalStateException("input must be an ObjectNode.");
    }
    input = replaceSecretsWithParentheses((ObjectNode) input, context, secretReplacer);
    input = replaceSecretsWithoutParentheses((ObjectNode) input, context, secretReplacer);
    return input;
  }

  private static ObjectNode replaceSecretsWithParentheses(
      ObjectNode input, SecretContext context, SecretReplacer secretReplacer) {
    walkJsonNode(
        input,
        (stringValue, fieldPath) -> {
          var secretVariableNameWithParenthesesMatcher =
              SECRET_PATTERN_PARENTHESES.matcher(stringValue);
          while (secretVariableNameWithParenthesesMatcher.find()) {
            stringValue =
                replaceTokens(
                    stringValue,
                    SECRET_PATTERN_PARENTHESES,
                    matcher -> resolveSecretValue(context, secretReplacer, matcher, fieldPath));
          }
          return stringValue;
        },
        new ArrayList<>());
    return input;
  }

  private static ObjectNode replaceSecretsWithoutParentheses(
      ObjectNode input, SecretContext context, SecretReplacer secretReplacer) {
    walkJsonNode(
        input,
        (stringValue, fieldPath) -> {
          var secretVariableNameWithParenthesesMatcher =
              SECRET_PATTERN_SECRETS.matcher(stringValue);
          while (secretVariableNameWithParenthesesMatcher.find()) {
            stringValue =
                replaceTokens(
                    stringValue,
                    SECRET_PATTERN_SECRETS,
                    matcher -> resolveSecretValue(context, secretReplacer, matcher, fieldPath));
          }
          return stringValue;
        },
        new ArrayList<>());
    return input;
  }

  private static void walkJsonNode(
      JsonNode input, BiFunction<String, List<String>, String> converter, List<String> fieldPath) {
    switch (input.getNodeType()) {
      case ARRAY -> walkArray((ArrayNode) input, converter, fieldPath);
      case OBJECT -> walkObject((ObjectNode) input, converter, fieldPath);
      default -> {}
    }
  }

  /**
   * Substitutes property names as well as values: the raw-text pass this replaced matched anywhere
   * in the document, key or value, so a placeholder written as a property name (e.g. {@code
   * {"{{secrets.KEY}}": "value"}}) resolved just as one written as a value did. Renaming is
   * deferred until after the entry is otherwise processed, and runs over a snapshot of the original
   * entries, since renaming a key while iterating the node's live property view would throw.
   */
  private static void walkObject(
      ObjectNode input,
      BiFunction<String, List<String>, String> converter,
      List<String> fieldPath) {
    for (Entry<String, JsonNode> entry : List.copyOf(input.properties())) {
      String key = entry.getKey();
      JsonNode value = entry.getValue();
      List<String> extendedFieldPath = new ArrayList<>(fieldPath);
      extendedFieldPath.add(key);

      if (value instanceof TextNode stringValue) {
        input.put(key, converter.apply(stringValue.asText(), extendedFieldPath));
      } else {
        walkJsonNode(value, converter, extendedFieldPath);
      }

      String newKey = converter.apply(key, extendedFieldPath);
      if (!newKey.equals(key)) {
        input.set(newKey, input.get(key));
        input.remove(key);
      }
    }
  }

  /** Recurses into array elements at arbitrary depth, mirroring {@link #walkJsonNode}. */
  private static void walkArray(
      ArrayNode arrayNode,
      BiFunction<String, List<String>, String> converter,
      List<String> fieldPath) {
    for (int i = 0; i < arrayNode.size(); i++) {
      JsonNode item = arrayNode.get(i);
      if (item instanceof TextNode stringValue) {
        arrayNode.set(i, new TextNode(converter.apply(stringValue.asText(), fieldPath)));
      } else {
        walkJsonNode(item, converter, fieldPath);
      }
    }
  }

  private static @Nullable String resolveSecretValue(
      SecretContext context,
      SecretReplacer secretReplacer,
      Matcher matcher,
      List<String> fieldPath) {
    var secretName = matcher.group("secret").trim();
    if (!secretName.isBlank()) {
      var result = secretReplacer.replaceSecrets(new Secret(secretName, fieldPath), context);
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
  public static List<Secret> retrieveSecretKeysInInput(ObjectNode input) {
    return keysIn(
        input, SECRET_PATTERN_PARENTHESES, SECRET_PATTERN_SECRETS, SecretReferenceUtil.PATTERN);
  }

  /**
   * Every secret name the given text declares in one of the two legacy forms. Excludes the new
   * {@code camunda.secrets.<name>} form, which the legacy providers never resolve, so that a caller
   * asking what the legacy providers were responsible for is not handed names they never held.
   */
  public static List<Secret> retrieveLegacySecretKeysInInput(ObjectNode input) {
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
  private static List<Secret> keysIn(ObjectNode input, Pattern... patterns) {
    List<Secret> result = new ArrayList<>();
    walkJsonNode(
        input,
        (stringValue, fieldPath) -> {
          result.addAll(
              Stream.of(patterns)
                  .map(pattern -> pattern.matcher(stringValue))
                  .flatMap(Matcher::results)
                  .map(matchResult -> matchResult.group("secret").trim())
                  .distinct()
                  .map(secretName -> new Secret(secretName, fieldPath))
                  .toList());
          return stringValue;
        },
        new ArrayList<>());
    return result;
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
    return Objects.isNull(input)
        ? List.of()
        : Stream.of(patterns)
            .map(pattern -> pattern.matcher(input))
            .flatMap(Matcher::results)
            .map(matchResult -> matchResult.group("secret").trim())
            .distinct()
            .toList();
  }
}
