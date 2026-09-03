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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/** Utility class to replace secrets in strings. */
public class SecretUtil {

  // One pattern, so that one scan consumes each reference whole: scanning per form or per caller
  // lets a narrower alternative re-read the inside of a wider match, as a name never declared.
  private static final Pattern REFERENCE =
      Pattern.compile(
          "\\{\\{\\s*secrets\\.(?<braced>\\S+?)\\s*}}"
              + "|camunda\\.secrets\\.(?<reference>[\\p{Alnum}_-]+)"
              + "|secrets\\.(?<bare>([a-zA-Z0-9]+[\\/._-])*[a-zA-Z0-9]+)");

  /**
   * Substitutes every legacy secret reference in the tree, in place, and returns the same node.
   *
   * <p>One walk, one scan per string: a second pass over already-substituted text is what let a
   * narrower form re-read the inside of a reference the first pass had consumed (and, when the walk
   * renames a property, made the second pass iterate a reordered snapshot of the object).
   */
  public static JsonNode replaceSecrets(
      JsonNode input, SecretContext context, SecretReplacer secretReplacer) {
    if (input == null) {
      throw new IllegalStateException("input cant be null.");
    }
    if (!input.isObject()) {
      throw new IllegalStateException("input must be an ObjectNode.");
    }
    Map<Secret, String> resolutions = new HashMap<>();
    walkJsonNode(
        input,
        (stringValue, fieldPath) ->
            replaceTokens(
                stringValue,
                REFERENCE,
                matcher -> {
                  var value =
                      resolve(legacyName(matcher), fieldPath, context, secretReplacer, resolutions);
                  return value == null ? matcher.group() : value;
                }),
        new ArrayList<>());
    return input;
  }

  /**
   * Asks the replacer at most once per secret <em>and field path</em>: the same name is a separate
   * question at a different path, since that is the granularity the allow-list authorizes at.
   */
  private static @Nullable String resolve(
      @Nullable String name,
      List<String> fieldPath,
      SecretContext context,
      SecretReplacer secretReplacer,
      Map<Secret, String> resolutions) {
    if (name == null) {
      return null;
    }
    var secret = new Secret(name, fieldPath);
    if (!resolutions.containsKey(secret)) {
      resolutions.put(secret, secretReplacer.replaceSecrets(secret, context));
    }
    return resolutions.get(secret);
  }

  /**
   * The name a legacy provider is asked for, or {@code null} for a {@code camunda.secrets.<name>}
   * reference — matched only so that one scan consumes it whole, and left in place for the cluster
   * to resolve.
   */
  private static @Nullable String legacyName(MatchResult match) {
    var braced = match.group("braced");
    return braced != null ? braced : match.group("bare");
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
    // Map.entry(...) reads each key/value pair eagerly, decoupling the snapshot from the live
    // map: input.properties() entries are backed by the same map nodes ObjectNode#set/remove
    // mutate, so a snapshot that merely copies the entry objects (e.g. List.copyOf(...)) would
    // still observe later renames through entries for keys processed earlier in this same loop.
    for (Entry<String, JsonNode> entry :
        input.properties().stream().map(e -> Map.entry(e.getKey(), e.getValue())).toList()) {
      String key = entry.getKey();
      JsonNode value = entry.getValue();
      List<String> extendedFieldPath = new ArrayList<>(fieldPath);
      extendedFieldPath.add(key);

      if (value instanceof TextNode stringValue) {
        input.set(key, new TextNode(converter.apply(stringValue.asText(), extendedFieldPath)));
      } else {
        walkJsonNode(value, converter, extendedFieldPath);
        // Reinserts this entry's own (possibly object/array-mutated-in-place, otherwise
        // untouched) value at its own original key, in snapshot order. Without this, a key
        // rename earlier in this same loop that happens to collide with this entry's still
        // unprocessed key would silently overwrite this entry before its own turn came, and this
        // entry's non-text value — having no put/set of its own — would never overwrite it back.
        input.set(key, value);
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
    return keysIn(input, "braced", "bare").findAny().isPresent();
  }

  /**
   * Every secret the given tree declares, in any of the three forms, each scoped to the field path
   * it occupies. The {@code camunda.secrets.<name>} form is reported too, though the cluster
   * resolves that one rather than this class.
   */
  public static List<Secret> retrieveSecretKeysInInput(ObjectNode input) {
    return keysIn(input, "braced", "bare", "reference");
  }

  /**
   * Every secret the given tree declares in one of the two legacy forms, each scoped to the field
   * path it occupies. Excludes the {@code camunda.secrets.<name>} form, which the legacy providers
   * never resolve, so that a caller asking what the legacy providers were responsible for is not
   * handed names they never held.
   */
  public static List<Secret> retrieveLegacySecretKeysInInput(ObjectNode input) {
    return keysIn(input, "braced", "bare");
  }

  private static List<Secret> keysIn(ObjectNode input, String... groups) {
    List<Secret> result = new ArrayList<>();
    walkJsonNode(
        input,
        (stringValue, fieldPath) -> {
          keysIn(stringValue, groups).map(name -> new Secret(name, fieldPath)).forEach(result::add);
          return stringValue;
        },
        new ArrayList<>());
    return result;
  }

  /**
   * Every secret name the given text declares, in any of the three forms, read by the same scan
   * {@link #replaceSecrets} uses: a name that method asks a legacy provider for appears here
   * spelled exactly as it asks for it, and no name appears that the scan never read. The {@code
   * camunda.secrets.<name>} form is reported too, though the cluster resolves that one rather than
   * this class.
   */
  public static List<String> retrieveSecretKeysInInput(String input) {
    return keysIn(input, "braced", "bare", "reference").toList();
  }

  /**
   * Every secret name the given text declares in one of the two legacy forms. Excludes the {@code
   * camunda.secrets.<name>} form, which the legacy providers never resolve, so that a caller asking
   * what the legacy providers were responsible for is not handed names they never held.
   */
  public static List<String> retrieveLegacySecretKeysInInput(String input) {
    return keysIn(input, "braced", "bare").toList();
  }

  private static Stream<String> keysIn(String input, String... groups) {
    return input == null
        ? Stream.of()
        : REFERENCE
            .matcher(input)
            .results()
            .flatMap(match -> Stream.of(groups).map(match::group))
            .filter(Objects::nonNull)
            .distinct();
  }
}
