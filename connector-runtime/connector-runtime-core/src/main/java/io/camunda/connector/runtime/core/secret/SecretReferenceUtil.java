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

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds and replaces secret references of the form {@code camunda.secrets.<name>}.
 *
 * <p>This is the new, cluster-backed form. For the legacy {@code {{secrets.X}}} and bare {@code
 * secrets.X} forms see {@link SecretUtil}; the two are separate mechanisms reading separate stores.
 */
public final class SecretReferenceUtil {

  /** The prefix the resolve endpoint expects on every reference. */
  public static final String REFERENCE_PREFIX = "camunda.secrets.";

  /**
   * Mirrors the engine's reference charset. The engine detects references on the parsed FEEL
   * expression rather than with a regex, so this is used only to locate an already-detected
   * reference in evaluated text — never to decide that something is a reference.
   */
  static final Pattern PATTERN = Pattern.compile("camunda\\.secrets\\.(?<secret>[\\p{Alnum}_]+)");

  private SecretReferenceUtil() {}

  /** Builds the whole reference string the resolve endpoint expects for a bare secret name. */
  public static String reference(String secretName) {
    return REFERENCE_PREFIX + secretName;
  }

  /** Returns every distinct whole reference appearing in {@code input}. */
  public static List<String> findReferences(String input) {
    return Objects.isNull(input)
        ? List.of()
        : PATTERN.matcher(input).results().map(MatchResult::group).distinct().toList();
  }

  /**
   * Replaces every reference in {@code input} that {@code values} has an entry for. A reference
   * with no entry is left exactly as it is, so text that merely looks like a reference — and a
   * reference that could not be resolved — survives unchanged rather than becoming empty.
   *
   * <p>One pass over the input, so a resolved value is never itself rescanned: a secret whose value
   * contains reference-shaped text must stay opaque. The pattern is greedy, so the longest
   * reference at a position matches; {@code camunda.secrets.TOKEN} cannot be substituted inside
   * {@code camunda.secrets.TOKEN_V2}.
   */
  public static String replaceReferences(String input, Map<String, String> values) {
    if (input == null) {
      return null;
    }
    return replaceTokens(input, m -> values.getOrDefault(m.group(), m.group()));
  }

  private static String replaceTokens(String original, Function<Matcher, String> converter) {
    Matcher matcher = PATTERN.matcher(original);
    if (!matcher.find()) {
      return original;
    }
    int lastIndex = 0;
    StringBuilder output = new StringBuilder();
    do {
      output.append(original, lastIndex, matcher.start()).append(converter.apply(matcher));
      lastIndex = matcher.end();
    } while (matcher.find());
    output.append(original, lastIndex, original.length());
    return output.toString();
  }
}
