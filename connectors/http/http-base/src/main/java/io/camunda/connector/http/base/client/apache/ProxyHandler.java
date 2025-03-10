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
package io.camunda.connector.http.base.client.apache;

import io.camunda.connector.api.error.ConnectorInputException;
import java.net.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;

public class ProxyHandler {
  record ProxyDetails(
      String protocol,
      String host,
      int port,
      String user,
      String password,
      String nonProxyHosts,
      boolean sourceIsSystemProperties) {}

  private static final List<String> PROTOCOLS = List.of("http", "https");
  private Map<String, ProxyDetails> proxyConfigForProtocols = new HashMap<>();
  private Map<String, CredentialsProvider> credentialsProvidersForProtocols = new HashMap<>();

  public ProxyHandler() {
    this.proxyConfigForProtocols = loadProxyConfig();
    this.credentialsProvidersForProtocols = initializeCredentialsProviders();
    this.syncEnvVarsToSystemProperties();
  }

  private Map<String, ProxyDetails> loadProxyConfig() {
    Map<String, ProxyDetails> config = new HashMap<>();
    for (String protocol : PROTOCOLS) {
      getConfigFromSystemProperties(protocol)
          .or(() -> getConfigFromEnvVars(protocol))
          .ifPresent(d -> config.put(protocol, d));
    }
    return config;
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
                protocol,
                System.getenv("CONNECTOR_" + protocol.toUpperCase() + "_PROXY_HOST"),
                Integer.parseInt(
                    System.getenv("CONNECTOR_" + protocol.toUpperCase() + "_PROXY_PORT")),
                System.getenv()
                    .getOrDefault("CONNECTOR_" + protocol.toUpperCase() + "_PROXY_USER", null),
                System.getenv()
                    .getOrDefault("CONNECTOR_" + protocol.toUpperCase() + "_PROXY_PASSWORD", null),
                System.getenv("CONNECTOR_HTTP_PROXY_NON_PROXY_HOSTS"),
                false));
      } catch (NumberFormatException e) {
        throw new ConnectorInputException("Invalid proxy port in environment variables", e);
      }
    }
    return Optional.empty();
  }

  private Optional<ProxyDetails> getConfigFromSystemProperties(String protocol) {
    if (StringUtils.isNotBlank(System.getProperty(protocol + ".proxyHost"))
        && StringUtils.isNotBlank(System.getProperty(protocol + ".proxyPort"))) {
      try {
        return Optional.of(
            new ProxyDetails(
                protocol,
                System.getProperty(protocol + ".proxyHost"),
                Integer.parseInt(System.getProperty(protocol + ".proxyPort")),
                System.getProperty(protocol + ".proxyUser"),
                System.getProperty(protocol + ".proxyPassword"),
                System.getProperty(protocol + ".nonProxyHosts"),
                true));
      } catch (NumberFormatException e) {
        throw new ConnectorInputException(
            "Invalid proxy port in system properties for " + protocol, e);
      }
    }
    return Optional.empty();
  }

  public CredentialsProvider getCredentialsProvider(String protocol) {
    return credentialsProvidersForProtocols.getOrDefault(protocol, new BasicCredentialsProvider());
  }

  /*
   Set the system properties for the proxy settings from env vars, if needed, because
   the default proxy selector does enforce a set of System Properties related to proxy settings.
   https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/ProxySelector.html
  */
  private void syncEnvVarsToSystemProperties() {
    for (Map.Entry<String, ProxyDetails> entry : proxyConfigForProtocols.entrySet()) {
      ProxyDetails p = entry.getValue();
      if (!p.sourceIsSystemProperties()) {
        setSystemPropertyIfUnset(p.protocol + ".proxyHost", p.host());
        setSystemPropertyIfUnset(p.protocol + ".proxyPort", String.valueOf(p.port()));
        setSystemPropertyIfUnset(p.protocol + ".proxyUser", p.user());
        setSystemPropertyIfUnset(p.protocol + ".proxyPassword", String.valueOf(p.password()));
        setSystemPropertyIfUnset(
            "http.nonProxyHosts",
            p.nonProxyHosts()); // The HTTPS protocol handler will use the same nonProxyHosts
        // property as the HTTP protocol. See here:
        // https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/net/doc-files/net-properties.html#Proxies
      }
    }
  }

  private void setSystemPropertyIfUnset(String name, String value) {
    if (System.getProperty(name) == null || System.getProperty(name).isBlank()) {
      System.setProperty(name, value != null ? value : "");
    }
  }
}
