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

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Utility for matching hostnames against non-proxy host patterns and retrieving configured
 * patterns. Supports the same wildcard syntax used by the Java system property {@code
 * http.nonProxyHosts} and the {@code CONNECTOR_HTTP_NON_PROXY_HOSTS} environment variable.
 *
 * <p>Patterns are pipe-separated (e.g. {@code localhost|*.internal.com}) and support {@code *} as a
 * wildcard.
 */
public class NonProxyHosts {

  public static final String CONNECTOR_HTTP_NON_PROXY_HOSTS_ENV_VAR =
      "CONNECTOR_HTTP_NON_PROXY_HOSTS";

  private static final ConcurrentHashMap<String, Pattern> PATTERN_CACHE = new ConcurrentHashMap<>();

  private NonProxyHosts() {}

  /**
   * Returns {@code true} if the given hostname matches any configured non-proxy host pattern from
   * the system property {@code http.nonProxyHosts} or the environment variable {@code
   * CONNECTOR_HTTP_NON_PROXY_HOSTS}.
   */
  public static boolean isNonProxyHost(String hostname) {
    return getNonProxyHostsPatterns()
        .anyMatch(
            nonProxyHostsPattern -> toPattern(nonProxyHostsPattern).matcher(hostname).matches());
  }

  /**
   * Returns configured non-proxy host patterns from the system property {@code http.nonProxyHosts}
   * and the environment variable {@code CONNECTOR_HTTP_NON_PROXY_HOSTS}.
   *
   * @return a stream of non-proxy host patterns; null values are filtered out
   */
  public static Stream<String> getNonProxyHostsPatterns() {
    return Stream.of(
            System.getProperty("http.nonProxyHosts"),
            System.getenv(CONNECTOR_HTTP_NON_PROXY_HOSTS_ENV_VAR))
        .filter(Objects::nonNull);
  }

  /**
   * Returns configured non-proxy host patterns converted to regex patterns, from the system
   * property {@code http.nonProxyHosts} and the environment variable {@code
   * CONNECTOR_HTTP_NON_PROXY_HOSTS}.
   *
   * @return a stream of regex patterns matching non-proxy hosts
   */
  public static Stream<String> getNonProxyHostRegexPatterns() {
    return getNonProxyHostsPatterns().map(NonProxyHosts::toRegex);
  }

  /**
   * Converts a non-proxy hosts pattern string (pipe-separated, with {@code *} wildcards) into a
   * regex pattern string. Each token is split by {@code *}, each part is regex-escaped via {@link
   * Pattern#quote}, and parts are rejoined with {@code .*}. Tokens are then rejoined with {@code |}
   * for alternation. This ensures that regex metacharacters such as {@code .} are treated as
   * literals rather than regex constructs.
   */
  static String toRegex(String nonProxyHosts) {
    return Arrays.stream(nonProxyHosts.split("\\|", -1))
        .map(
            token ->
                Arrays.stream(token.split("\\*", -1))
                    .map(Pattern::quote)
                    .collect(Collectors.joining(".*")))
        .collect(Collectors.joining("|"));
  }

  /**
   * Returns a precompiled {@link Pattern} for the given non-proxy hosts pattern string. Results are
   * cached to avoid recompiling the same pattern on every request.
   */
  private static Pattern toPattern(String nonProxyHosts) {
    return PATTERN_CACHE.computeIfAbsent(nonProxyHosts, s -> Pattern.compile(toRegex(s)));
  }
}
