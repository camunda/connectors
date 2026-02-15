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
package io.camunda.connector.http.client.client.jdk.proxy;

import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import java.net.http.HttpClient;

/**
 * Configures JDK {@link HttpClient} instances with proxy settings from a {@link
 * ProxyConfiguration}. This provides the same proxy configuration as the Apache HTTP client proxy
 * support.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * var proxy = new JdkHttpClientProxyConfigurator(new ProxyConfiguration());
 * HttpClient.Builder builder = proxy.configure(HttpClient.newBuilder());
 * // ... further customization ...
 * HttpClient client = builder.build();
 * }</pre>
 *
 * <p>Or as a static one-liner when no further customization is needed:
 *
 * <pre>{@code
 * HttpClient client = JdkHttpClientProxyConfigurator.newHttpClient(new ProxyConfiguration());
 * }</pre>
 *
 * @see ProxyConfiguration for the list of supported environment variables
 */
public class JdkHttpClientProxyConfigurator {

  private final ProxyConfiguration proxyConfiguration;

  public JdkHttpClientProxyConfigurator(ProxyConfiguration proxyConfiguration) {
    this.proxyConfiguration = proxyConfiguration;
  }

  /**
   * Configures the given {@link HttpClient.Builder} with proxy settings from the {@link
   * ProxyConfiguration}. Sets up both the {@link java.net.ProxySelector} and {@link
   * java.net.Authenticator} if proxy configuration is present. If no proxy is configured, the
   * builder is returned unchanged.
   *
   * <p>This is the primary method to use when the caller needs to further customize the builder
   * before building the client.
   *
   * @param builder the HttpClient.Builder to configure
   * @return the same builder, for chaining
   */
  public HttpClient.Builder configure(HttpClient.Builder builder) {
    boolean hasProxy =
        proxyConfiguration.getProxyDetails(ProxyConfiguration.HTTP).isPresent()
            || proxyConfiguration.getProxyDetails(ProxyConfiguration.HTTPS).isPresent();

    if (hasProxy) {
      builder
          .proxy(new JdkProxySelector(proxyConfiguration))
          .authenticator(new JdkProxyAuthenticator(proxyConfiguration));
    }

    return builder;
  }

  /**
   * Creates a new {@link HttpClient} with proxy settings applied. Convenience method equivalent to
   * {@code configure(HttpClient.newBuilder()).build()}.
   *
   * @return a new HttpClient
   */
  public HttpClient newHttpClient() {
    return configure(HttpClient.newBuilder()).build();
  }

  /**
   * Creates a new {@link HttpClient} with proxy settings from the given {@link ProxyConfiguration}.
   * Static convenience method for one-liner usage.
   *
   * @param proxyConfiguration the proxy configuration to use
   * @return a new HttpClient
   */
  public static HttpClient newHttpClient(ProxyConfiguration proxyConfiguration) {
    return new JdkHttpClientProxyConfigurator(proxyConfiguration).newHttpClient();
  }
}
