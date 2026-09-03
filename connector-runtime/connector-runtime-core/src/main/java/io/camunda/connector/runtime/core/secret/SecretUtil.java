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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/** Utility class to replace secrets in strings. */
public class SecretUtil {

  private static final JsonStringEncoder encoder = JsonStringEncoder.getInstance();

  // One pattern, so that one scan consumes each reference whole: scanning per form or per caller
  // lets a narrower alternative re-read the inside of a wider match, as a name never declared.
  private static final Pattern REFERENCE =
      Pattern.compile(
          "\\{\\{\\s*secrets\\.(?<braced>\\S+?)\\s*}}"
              + "|camunda\\.secrets\\.(?<reference>[\\p{Alnum}_-]+)"
              + "|secrets\\.(?<bare>([a-zA-Z0-9]+[\\/._-])*[a-zA-Z0-9]+)");

  public static String replaceSecrets(
      String input, SecretContext context, SecretReplacer secretReplacer) {
    if (input == null) {
      throw new IllegalStateException("input cant be null.");
    }
    Map<String, String> resolutions = new HashMap<>();
    return replaceTokens(
        input,
        REFERENCE,
        matcher -> {
          String value = resolve(legacyName(matcher), context, secretReplacer, resolutions);
          return value == null ? matcher.group() : new String(encoder.quoteAsString(value));
        });
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

  private static @Nullable String resolve(
      @Nullable String name,
      SecretContext context,
      SecretReplacer secretReplacer,
      Map<String, String> resolutions) {
    if (name == null) {
      return null;
    }
    if (!resolutions.containsKey(name)) {
      resolutions.put(name, secretReplacer.replaceSecrets(name, context));
    }
    return resolutions.get(name);
  }

  private static @Nullable String legacyName(MatchResult match) {
    var braced = match.group("braced");
    return braced != null ? braced : match.group("bare");
  }

  /**
   * Whether the text contains a legacy secret reference, in either of its two spellings. Used where
   * the legacy form is not supported and has to be reported rather than silently left in place.
   */
  public static boolean containsLegacySecretReference(String input) {
    return keysIn(input, "braced", "bare").findAny().isPresent();
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

  // Longest secret first: masking a shorter secret that prefixes a longer one would destroy the
  // longer match and publish its remainder, e.g. "x" before "xSUPERSECRET" leaves "***SUPERSECRET".
  public static String hideSecretsFromMessage(String message, List<String> secrets) {
    if (message == null) {
      return "";
    }
    return secrets.stream()
        .filter(secret -> !secret.isEmpty())
        .sorted(Comparator.comparingInt(String::length).reversed())
        .reduce(message, (newMessage, nextSecret) -> newMessage.replace(nextSecret, "***"));
  }
}
