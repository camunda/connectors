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

import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;

/**
 * This class is responsible for handling proxy configuration for the Apache HTTP client. It
 * delegates environment variable reading to {@link ProxyConfiguration} and provides Apache-specific
 * credentials providers.
 *
 * @see ProxyConfiguration for the list of supported environment variables
 */
public class ProxyHandler {

  public record ProxyDetails(String scheme, String host, int port, String user, String password) {
    static ProxyDetails fromConfiguration(ProxyConfiguration.ProxyDetails details) {
      return new ProxyDetails(
          details.scheme(), details.host(), details.port(), details.user(), details.password());
    }
  }

  public static final String HTTP = ProxyConfiguration.HTTP;
  public static final String HTTPS = ProxyConfiguration.HTTPS;

  private final ProxyConfiguration proxyConfiguration;
  private final Map<String, CredentialsProvider> credentialsProvidersForProtocols;

  public ProxyHandler() {
    this.proxyConfiguration = new ProxyConfiguration();
    this.credentialsProvidersForProtocols = initializeCredentialsProviders();
  }

  public Optional<ProxyDetails> getProxyDetails(String protocol) {
    return proxyConfiguration.getProxyDetails(protocol).map(ProxyDetails::fromConfiguration);
  }

  private Map<String, CredentialsProvider> initializeCredentialsProviders() {
    Map<String, CredentialsProvider> providers = new HashMap<>();
    for (String protocol : List.of(HTTP, HTTPS)) {
      proxyConfiguration
          .getProxyDetails(protocol)
          .filter(ProxyConfiguration.ProxyDetails::hasCredentials)
          .ifPresent(
              p -> {
                BasicCredentialsProvider provider = new BasicCredentialsProvider();
                provider.setCredentials(
                    new AuthScope(p.host(), p.port()),
                    new UsernamePasswordCredentials(p.user(), p.password().toCharArray()));
                providers.put(protocol, provider);
              });
    }
    return providers;
  }

  public CredentialsProvider getCredentialsProvider(String protocol) {
    return credentialsProvidersForProtocols.getOrDefault(protocol, new BasicCredentialsProvider());
  }
}
