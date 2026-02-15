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

import io.camunda.connector.api.error.ConnectorInputException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

/**
 * Reads proxy configuration from environment variables. This class extracts the shared logic of
 * reading proxy settings that can be used by both Apache and JDK HTTP client implementations.
 *
 * <p>Supported environment variables:
 *
 * <ul>
 *   <li>CONNECTOR_HTTP(S)_PROXY_SCHEME (default: http)
 *   <li>CONNECTOR_HTTP(S)_PROXY_HOST
 *   <li>CONNECTOR_HTTP(S)_PROXY_PORT
 *   <li>CONNECTOR_HTTP(S)_PROXY_USER
 *   <li>CONNECTOR_HTTP(S)_PROXY_PASSWORD
 *   <li>CONNECTOR_HTTP_NON_PROXY_HOSTS
 * </ul>
 */
public class ProxyConfiguration {

  public static final String CONNECTOR_HTTP_NON_PROXY_HOSTS_ENV_VAR =
      "CONNECTOR_HTTP_NON_PROXY_HOSTS";
  public static final String HTTP = "http";
  public static final String HTTPS = "https";
  private static final String DEFAULT_SCHEME = HTTP;
  private static final List<String> PROTOCOLS = List.of(HTTP, HTTPS);

  private final Map<String, ProxyDetails> proxyConfigForProtocols;

  public ProxyConfiguration() {
    this.proxyConfigForProtocols = loadProxyConfig();
  }

  private Map<String, ProxyDetails> loadProxyConfig() {
    Map<String, ProxyDetails> config = new HashMap<>();
    for (String protocol : PROTOCOLS) {
      getConfigFromEnvVars(protocol).ifPresent(d -> config.put(protocol, d));
    }
    return config;
  }

  public Optional<ProxyDetails> getProxyDetails(String protocol) {
    return Optional.ofNullable(proxyConfigForProtocols.get(protocol));
  }

  private Optional<ProxyDetails> getConfigFromEnvVars(String protocol) {
    final String prefix = "CONNECTOR_" + protocol.toUpperCase() + "_PROXY_";

    if (StringUtils.isNotBlank(System.getenv(prefix + "HOST"))
        && StringUtils.isNotBlank(System.getenv(prefix + "PORT"))) {

      return Optional.of(
          new ProxyDetails(
              System.getenv().getOrDefault(prefix + "SCHEME", DEFAULT_SCHEME),
              System.getenv(prefix + "HOST"),
              parsePort(prefix + "PORT"),
              System.getenv().getOrDefault(prefix + "USER", null),
              System.getenv().getOrDefault(prefix + "PASSWORD", null)));
    }

    return Optional.empty();
  }

  private int parsePort(String portEnvVar) {
    try {
      return Integer.parseInt(System.getenv(portEnvVar));
    } catch (NumberFormatException e) {
      throw new ConnectorInputException(
          "Invalid proxy port in environment variable " + portEnvVar, e);
    }
  }

  public record ProxyDetails(String scheme, String host, int port, String user, String password) {
    public boolean hasCredentials() {
      return StringUtils.isNotBlank(user) && StringUtils.isNotEmpty(password);
    }
  }
}
