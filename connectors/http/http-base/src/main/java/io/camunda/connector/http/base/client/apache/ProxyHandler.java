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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.DefaultSchemePortResolver;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.routing.DefaultProxyRoutePlanner;
import org.apache.hc.client5.http.impl.routing.SystemDefaultRoutePlanner;
import org.apache.hc.client5.http.routing.HttpRoutePlanner;
import org.apache.hc.core5.http.HttpHost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProxyHandler {
  record ProxyDetails(
      String protocol,
      String host,
      int port,
      String user,
      char[] password,
      String nonProxyHosts,
      boolean sourceIsSystemProperties) {}

  private static final List<String> PROTOCOLS = List.of("http", "https");
  private Map<String, ProxyDetails> proxyConfigForProtocols = new HashMap<>();
  private Map<String, CredentialsProvider> credentialsProvidersForProtocols = new HashMap<>();
  private static final Logger LOGGER = LoggerFactory.getLogger(ProxyHandler.class);

  public ProxyHandler() {
    this.proxyConfigForProtocols = loadProxyConfig();
    this.credentialsProvidersForProtocols = initializeCredentialsProviders();
  }

  private Map<String, ProxyDetails> loadProxyConfig() {
    Map<String, ProxyDetails> config = new HashMap<>();
    for (String protocol : PROTOCOLS) {
      Optional<ProxyDetails> details =
          getConfigFromSystemProperties(protocol).or(() -> getConfigFromEnvVars(protocol));
      details.ifPresent(d -> config.put(protocol, d));
    }
    return config;
  }

  private Map<String, CredentialsProvider> initializeCredentialsProviders() {
    Map<String, CredentialsProvider> providers = new HashMap<>();
    for (Map.Entry<String, ProxyDetails> entry : proxyConfigForProtocols.entrySet()) {
      ProxyDetails p = entry.getValue();
      if (StringUtils.isNotBlank(p.user()) && ArrayUtils.isNotEmpty(p.password())) {
        BasicCredentialsProvider provider = new BasicCredentialsProvider();
        provider.setCredentials(
            new AuthScope(p.host(), p.port()),
            new UsernamePasswordCredentials(p.user(), p.password()));
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
                    .getOrDefault("CONNECTOR_" + protocol.toUpperCase() + "_PROXY_USER", ""),
                System.getenv()
                    .getOrDefault("CONNECTOR_" + protocol.toUpperCase() + "_PROXY_PASSWORD", "")
                    .toCharArray(),
                System.getenv("CONNECTOR_" + protocol.toUpperCase() + "_PROXY_NON_PROXY_HOSTS"),
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
                Optional.ofNullable(System.getProperty(protocol + ".proxyPassword"))
                    .map(String::toCharArray)
                    .orElseGet(() -> new char[0]),
                System.getProperty(protocol + ".nonProxyHosts"),
                true));
      } catch (NumberFormatException e) {
        throw new ConnectorInputException(
            "Invalid proxy port in system properties", e); // TODO add protocol
      }
    }
    return Optional.empty();
  }

  public CredentialsProvider getCredentialsProvider(String protocol) {
    return credentialsProvidersForProtocols.getOrDefault(protocol, new BasicCredentialsProvider());
  }

  public HttpHost getProxyHost(String protocol, String requestUrl) {
    ProxyDetails p = proxyConfigForProtocols.get(protocol);
    if (p == null || doesTargetMatchNonProxy(protocol, requestUrl)) {
      LOGGER.debug("No proxy used for request URL: {}", requestUrl);
      return null;
    }
    LOGGER.debug(
        "Using proxy for {} - Host: {}, Port: {}, Source: {}",
        protocol,
        p.host(),
        p.port(),
        p.sourceIsSystemProperties() ? "System Properties" : "Environment Variables");

    return new HttpHost(p.protocol(), p.host(), p.port());
  }

  private boolean doesTargetMatchNonProxy(String protocol, String requestUri) {
    ProxyDetails p = proxyConfigForProtocols.get(protocol);
    if (p == null || p.nonProxyHosts() == null) {
      return false;
    }

    return Arrays.stream(p.nonProxyHosts().split("\\|"))
        .map(
            nonProxyHost -> {
              // If entry is "example.de", it should match example.de and *.example.de
              if (!nonProxyHost.contains("*")) {
                return "^(.*\\.)?" + Pattern.quote(nonProxyHost) + "$";
              }

              // Otherwise, process as wildcard domain
              return nonProxyHost.replace(".", "\\.").replace("*", ".*");
            })
        .anyMatch(regex -> requestUri.matches(regex));
  }

  public HttpRoutePlanner getRoutePlanner(String protocol, HttpHost proxyHost) {
    ProxyDetails p = proxyConfigForProtocols.get(protocol);

    if (p != null && p.sourceIsSystemProperties()) {
      LOGGER.debug("Using system default route planner for protocol: {}", protocol);
      return new SystemDefaultRoutePlanner(
          DefaultSchemePortResolver.INSTANCE, ProxySelector.getDefault());
    } else if (proxyHost != null) {
      LOGGER.debug(
          "Using default proxy route planner for protocol: {} with proxy: {}", protocol, proxyHost);
      return new DefaultProxyRoutePlanner(proxyHost);
    }

    LOGGER.debug("No proxy route planner used for protocol: {}", protocol);
    return null;
  }
}
