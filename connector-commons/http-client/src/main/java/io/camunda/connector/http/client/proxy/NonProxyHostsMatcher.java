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
package io.camunda.connector.http.client.proxy;

import static io.camunda.connector.http.client.proxy.ProxyConfiguration.CONNECTOR_HTTP_NON_PROXY_HOSTS_ENV_VAR;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * Matches hostnames against non-proxy host patterns. Supports the same wildcard syntax used by the
 * Java system property {@code http.nonProxyHosts} and the {@code CONNECTOR_HTTP_NON_PROXY_HOSTS}
 * environment variable.
 *
 * <p>Patterns are pipe-separated (e.g. {@code localhost|*.internal.com}) and support {@code *} as a
 * wildcard.
 */
public class NonProxyHostsMatcher {

  private NonProxyHostsMatcher() {}

  /**
   * Returns {@code true} if the given hostname matches any configured non-proxy host pattern from
   * the system property {@code http.nonProxyHosts} or the environment variable {@code
   * CONNECTOR_HTTP_NON_PROXY_HOSTS}.
   */
  public static boolean isNonProxyHost(String hostname) {
    return getNonProxyHostsPatterns()
        .filter(Objects::nonNull)
        .anyMatch(nonProxyHostsPattern -> hostname.matches(toRegex(nonProxyHostsPattern)));
  }

  /**
   * Converts a non-proxy hosts pattern string (pipe-separated, with {@code *} wildcards) into a
   * regex pattern. The conversion replaces {@code *} with {@code .*}.
   *
   * <p>Note: According to the documentation, patterns should only support wildcards at the start or
   * end (e.g., {@code *.example.com} or {@code example.*}), but this implementation currently
   * allows wildcards anywhere for backward compatibility with the previous {@code
   * ProxyRoutePlanner} implementation.
   *
   * <p>To align with documented behavior, this could be fixed by:
   *
   * <ol>
   *   <li>Escaping regex metacharacters (e.g., {@code .} should be literal, not "any character")
   *   <li>Only allowing {@code *} at the start or end of each pattern
   * </ol>
   *
   * <p>Example fix for documented behavior (wildcards only at start/end):
   *
   * <pre>{@code
   * static String toRegex(String nonProxyHosts) {
   *   // Split by pipe to handle each pattern separately
   *   String[] patterns = nonProxyHosts.split("\\|");
   *   StringBuilder result = new StringBuilder();
   *
   *   for (int i = 0; i < patterns.length; i++) {
   *     String pattern = patterns[i].trim();
   *
   *     // Check if pattern starts or ends with wildcard
   *     boolean startsWithWildcard = pattern.startsWith("*");
   *     boolean endsWithWildcard = pattern.endsWith("*");
   *
   *     // Remove wildcards for processing
   *     if (startsWithWildcard) {
   *       pattern = pattern.substring(1);
   *     }
   *     if (endsWithWildcard) {
   *       pattern = pattern.substring(0, pattern.length() - 1);
   *     }
   *
   *     // Escape the pattern to make it literal (escapes ., ?, etc.)
   *     String escaped = Pattern.quote(pattern);
   *
   *     // Build regex with wildcards only at allowed positions
   *     if (startsWithWildcard) {
   *       result.append(".*");
   *     }
   *     result.append(escaped);
   *     if (endsWithWildcard) {
   *       result.append(".*");
   *     }
   *
   *     // Add pipe separator if not the last pattern
   *     if (i < patterns.length - 1) {
   *       result.append("|");
   *     }
   *   }
   *
   *   return result.toString();
   * }
   * }</pre>
   */
  static String toRegex(String nonProxyHosts) {
    return nonProxyHosts.replace("*", ".*");
  }

  private static Stream<String> getNonProxyHostsPatterns() {
    return Stream.of(
        System.getProperty("http.nonProxyHosts"),
        System.getenv(CONNECTOR_HTTP_NON_PROXY_HOSTS_ENV_VAR));
  }
}
