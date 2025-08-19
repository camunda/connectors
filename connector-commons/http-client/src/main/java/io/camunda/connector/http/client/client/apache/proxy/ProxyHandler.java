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
package io.camunda.connector.http.client.client.apache.proxy;

import io.camunda.connector.api.error.ConnectorInputException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;

/**
 * This class is responsible for handling proxy configuration. It reads the proxy configuration from
 * environment variables and provides the proxy details and credentials provider for a given
 * protocol. Here's the list of environment variables that are used to configure the proxy:
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
 * The proxy configuration can be set for both HTTP and HTTPS protocols (for the target URL),
 * allowing different proxy configurations for each protocol.
 */
public class ProxyHandler {
  public record ProxyDetails(String scheme, String host, int port, String user, String password) {}

  public static final String CONNECTOR_HTTP_NON_PROXY_HOSTS_ENV_VAR =
      "CONNECTOR_HTTP_NON_PROXY_HOSTS";
  public static final String HTTP = "http";
  public static final String HTTPS = "https";
  private static final String DEFAULT_SCHEME = HTTP;
  private static final List<String> PROTOCOLS = List.of(HTTP, HTTPS);
  private Map<String, ProxyDetails> proxyConfigForProtocols = new HashMap<>();
  private Map<String, CredentialsProvider> credentialsProvidersForProtocols = new HashMap<>();

  public ProxyHandler() {
    this.proxyConfigForProtocols = loadProxyConfig();
    this.credentialsProvidersForProtocols = initializeCredentialsProviders();
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

  private Map<String, CredentialsProvider> initializeCredentialsProviders() {
    Map<String, CredentialsProvider> providers = new HashMap<>();
    for (Map.Entry<String, ProxyDetails> entry : proxyConfigForProtocols.entrySet()) {
      ProxyDetails p = entry.getValue();
      if (StringUtils.isNotBlank(p.user()) && StringUtils.isNotEmpty(p.password())) {
        BasicCredentialsProvider provider = new BasicCredentialsProvider();
        provider.setCredentials(
            new AuthScope(p.host(), p.port()),
            new UsernamePasswordCredentials(p.user(), p.password().toCharArray()));
        providers.put(entry.getKey(), provider);
      }
    }
    return providers;
  }

  private Optional<ProxyDetails> getConfigFromEnvVars(String protocol) {
    if (StringUtils.isNotBlank(System.getenv("CONNECTOR_" + protocol.toUpperCase() + "_PROXY_HOST"))
        && StringUtils.isNotBlank(
            System.getenv("CONNECTOR_" + protocol.toUpperCase() + "_PROXY_PORT"))) {
      try {
        return Optional.of(
            new ProxyDetails(
                System.getenv()
                    .getOrDefault(
                        "CONNECTOR_" + protocol.toUpperCase() + "_PROXY_SCHEME", DEFAULT_SCHEME),
                System.getenv("CONNECTOR_" + protocol.toUpperCase() + "_PROXY_HOST"),
                Integer.parseInt(
                    System.getenv("CONNECTOR_" + protocol.toUpperCase() + "_PROXY_PORT")),
                System.getenv()
                    .getOrDefault("CONNECTOR_" + protocol.toUpperCase() + "_PROXY_USER", null),
                System.getenv()
                    .getOrDefault(
                        "CONNECTOR_" + protocol.toUpperCase() + "_PROXY_PASSWORD", null)));
      } catch (NumberFormatException e) {
        throw new ConnectorInputException("Invalid proxy port in environment variables", e);
      }
    }
    return Optional.empty();
  }

  public CredentialsProvider getCredentialsProvider(String protocol) {
    return credentialsProvidersForProtocols.getOrDefault(protocol, new BasicCredentialsProvider());
  }
}
