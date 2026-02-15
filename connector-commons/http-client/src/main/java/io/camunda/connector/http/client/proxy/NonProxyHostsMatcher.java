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
