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
 *
 * <p>When {@code supportPlainProxyVars} is enabled, an additional set of "plain" environment
 * variables is checked first:
 *
 * <ul>
 *   <li>CONNECTOR_HTTP(S)_PLAIN_PROXY_SCHEME (default: http)
 *   <li>CONNECTOR_HTTP(S)_PLAIN_PROXY_HOST
 *   <li>CONNECTOR_HTTP(S)_PLAIN_PROXY_PORT
 *   <li>CONNECTOR_HTTP(S)_PLAIN_PROXY_USER
 *   <li>CONNECTOR_HTTP(S)_PLAIN_PROXY_PASSWORD
 * </ul>
 *
 * <p>If a complete plain proxy configuration is present (both HOST and PORT are set), all proxy
 * settings are read from the plain-prefixed environment variables. Otherwise, the standard proxy
 * environment variables are used as a fallback.
 */
public class EnvironmentProxyConfiguration implements ProxyConfiguration {

  private static final String DEFAULT_SCHEME = SCHEME_HTTP;
  private static final List<String> PROTOCOLS = List.of(SCHEME_HTTP, SCHEME_HTTPS);

  private final boolean supportPlainProxyVars;
  private final Map<String, ProxyDetails> proxyConfigForProtocols;

  private EnvironmentProxyConfiguration(boolean supportPlainProxyVars) {
    this.supportPlainProxyVars = supportPlainProxyVars;
    this.proxyConfigForProtocols = loadProxyConfig();
  }

  /**
   * Creates a configuration that reads from the standard {@code CONNECTOR_HTTP(S)_PROXY_*}
   * variables.
   */
  public static EnvironmentProxyConfiguration withDefaults() {
    return new EnvironmentProxyConfiguration(false);
  }

  /**
   * Creates a configuration that first checks the {@code CONNECTOR_HTTP(S)_PLAIN_PROXY_*} variables
   * and falls back to the standard {@code CONNECTOR_HTTP(S)_PROXY_*} variables.
   */
  public static EnvironmentProxyConfiguration withPlainProxySupport() {
    return new EnvironmentProxyConfiguration(true);
  }

  private Map<String, ProxyDetails> loadProxyConfig() {
    Map<String, ProxyDetails> config = new HashMap<>();
    for (String protocol : PROTOCOLS) {
      getConfigFromEnvVars(protocol).ifPresent(d -> config.put(protocol, d));
    }
    return config;
  }

  @Override
  public Optional<ProxyDetails> getProxyDetails(String protocol) {
    return Optional.ofNullable(proxyConfigForProtocols.get(ProtocolNormalizer.normalize(protocol)));
  }

  private Optional<ProxyDetails> getConfigFromEnvVars(String protocol) {
    if (supportPlainProxyVars) {
      final String plainPrefix = "CONNECTOR_" + protocol.toUpperCase() + "_PLAIN_PROXY_";
      Optional<ProxyDetails> plainDetails = readProxyDetails(plainPrefix);
      if (plainDetails.isPresent()) {
        return plainDetails;
      }
    }

    final String standardPrefix = "CONNECTOR_" + protocol.toUpperCase() + "_PROXY_";
    return readProxyDetails(standardPrefix);
  }

  private Optional<ProxyDetails> readProxyDetails(String prefix) {
    if (StringUtils.isNotBlank(System.getenv(prefix + "HOST"))
        && StringUtils.isNotBlank(System.getenv(prefix + "PORT"))) {

      return Optional.of(
          new ProxyDetails(
              System.getenv().getOrDefault(prefix + "SCHEME", DEFAULT_SCHEME),
              System.getenv(prefix + "HOST"),
              parsePort(prefix + "PORT"),
              System.getenv(prefix + "USER"),
              System.getenv(prefix + "PASSWORD")));
    }

    return Optional.empty();
  }

  private int parsePort(String portEnvVar) {
    try {
      int port = Integer.parseInt(System.getenv(portEnvVar));
      if (port < 1 || port > 65535) {
        throw new ConnectorInputException(
            "Proxy port in environment variable "
                + portEnvVar
                + " is out of range (1-65535): "
                + port);
      }
      return port;
    } catch (NumberFormatException e) {
      throw new ConnectorInputException(
          "Invalid proxy port in environment variable " + portEnvVar, e);
    }
  }
}
