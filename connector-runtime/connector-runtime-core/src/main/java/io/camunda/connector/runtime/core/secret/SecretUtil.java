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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Utility class to replace secrets in strings. */
public class SecretUtil {

  // One pattern, so that one scan consumes each reference whole: scanning per form or per caller
  // lets a narrower alternative re-read the inside of a wider match, as a name never declared.
  private static final Pattern REFERENCE =
      Pattern.compile(
          "\\{\\{\\s*secrets\\.(?<braced>\\S+?)\\s*}}"
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
          String value = resolve(name(matcher), context, secretReplacer, resolutions);
          return value == null ? matcher.group() : value;
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

  /** Asks the replacer at most once per name, for providers that meter or charge per lookup. */
  private static String resolve(
      String name,
      SecretContext context,
      SecretReplacer secretReplacer,
      Map<String, String> resolutions) {
    if (!resolutions.containsKey(name)) {
      resolutions.put(name, secretReplacer.replaceSecrets(name, context));
    }
    return resolutions.get(name);
  }

  private static String name(MatchResult match) {
    var braced = match.group("braced");
    return braced != null ? braced : match.group("bare");
  }

  /**
   * Every secret name the given text declares, in either form, read by the same scan {@link
   * #replaceSecrets} uses: a name that method asks a provider for appears here spelled exactly as
   * it asks for it, and no name appears that the scan never read. Feeding both from one scan is
   * what keeps the outbound allow-list and exception redaction agreeing with resolution.
   */
  public static List<String> retrieveSecretKeysInInput(String input) {
    return input == null
        ? List.of()
        : REFERENCE.matcher(input).results().map(SecretUtil::name).distinct().toList();
  }
}
