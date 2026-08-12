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
import io.camunda.connector.api.error.ConnectorInputException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Replaces the NEW form of secret in a string: {@code camunda.secrets.<name>}, resolved via {@link
 * SecretReferenceResolver} against the orchestration cluster. For the legacy {@code {{secrets.X}}}
 * / bare {@code secrets.X} form, see {@link SecretUtil} instead — the two are unrelated mechanisms
 * that never fall back onto one another.
 */
class SecretReferenceUtil {

  private static final JsonStringEncoder encoder = JsonStringEncoder.getInstance();

  // Mirrors the engine's own SecretReference.REFERENCE_PATTERN exactly, so detection cannot drift
  // from what the engine itself recognizes. Package-visible: SecretUtil.retrieveSecretKeysInInput
  // also matches this pattern, purely to keep the outbound allow-list the right size (see there).
  static final Pattern PATTERN = Pattern.compile("camunda\\.secrets\\.(?<secret>[\\p{Alnum}_]+)");

  private SecretReferenceUtil() {}

  /**
   * Returns every distinct whole reference found in {@code input} (e.g. {@code
   * "camunda.secrets.FOO"}), because that whole string is what {@code POST /v2/secrets/resolve}
   * expects.
   */
  static List<String> findReferences(String input) {
    return Objects.isNull(input)
        ? List.of()
        : PATTERN.matcher(input).results().map(MatchResult::group).distinct().toList();
  }

  /**
   * Strips the {@code camunda.secrets.} prefix (e.g. {@code "camunda.secrets.FOO"} to {@code
   * "FOO"}), so callers can check a reference against a bare-name-keyed {@link SecretFilter} such
   * as the outbound allow-list. {@code reference} is always the output of {@link
   * #findReferences(String)}, so it always matches. Throwing here instead of silently returning the
   * whole reference matters: passing the whole reference to {@link SecretFilter#isAllowed} would
   * never match a bare-name allow-list, so the secret would be refused instead of resolved, with
   * nothing to explain why.
   */
  static String bareName(String reference) {
    var matcher = PATTERN.matcher(reference);
    if (!matcher.matches()) {
      throw new IllegalArgumentException(
          "Not a whole camunda.secrets.<name> reference: " + reference);
    }
    return matcher.group("secret");
  }

  /**
   * Replaces every whole reference in {@code input}: one in {@code resolved} becomes its
   * (JSON-escaped) value; one in {@code refused} is left verbatim; one in neither throws {@link
   * ConnectorInputException}, matching how a missing legacy secret is handled. Both maps are keyed
   * by the whole reference, e.g. {@code "camunda.secrets.FOO"}.
   */
  static String replaceReferences(String input, Map<String, String> resolved, Set<String> refused) {
    var matcher = PATTERN.matcher(input);
    while (matcher.find()) {
      input = SecretUtil.replaceTokens(input, PATTERN, m -> resolveReference(m, resolved, refused));
    }
    return input;
  }

  private static String resolveReference(
      Matcher matcher, Map<String, String> resolved, Set<String> refused) {
    var reference = matcher.group();
    if (resolved.containsKey(reference)) {
      return new String(encoder.quoteAsString(resolved.get(reference)));
    }
    if (refused.contains(reference)) {
      return reference;
    }
    throw new ConnectorInputException(
        String.format("Secret with name '%s' is not available", matcher.group("secret")));
  }
}
