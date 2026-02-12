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

import io.camunda.connector.http.client.proxy.NonProxyHostsMatcher;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link ProxySelector} implementation for the JDK {@link java.net.http.HttpClient} that uses the
 * same environment variable configuration as the Apache HTTP client proxy support.
 *
 * <p>Supports both HTTP and HTTPS proxy configurations with different settings per protocol, and
 * honors non-proxy host patterns from both the {@code http.nonProxyHosts} system property and the
 * {@code CONNECTOR_HTTP_NON_PROXY_HOSTS} environment variable.
 *
 * @see ProxyConfiguration for the list of supported environment variables
 * @see NonProxyHostsMatcher for non-proxy host matching logic
 */
public class JdkProxySelector extends ProxySelector {
  private static final Logger LOG = LoggerFactory.getLogger(JdkProxySelector.class);
  private static final List<Proxy> NO_PROXY = List.of(Proxy.NO_PROXY);

  private final ProxyConfiguration proxyConfiguration;

  public JdkProxySelector(ProxyConfiguration proxyConfiguration) {
    this.proxyConfiguration = proxyConfiguration;
  }

  @Override
  public List<Proxy> select(URI uri) {
    if (uri == null) {
      throw new IllegalArgumentException("URI must not be null");
    }

    String host = uri.getHost();
    if (host != null && NonProxyHostsMatcher.isNonProxyHost(host)) {
      LOG.debug(
          "Not using proxy for target host [{}] as it matched either system properties (http.nonProxyHosts) or environment variables ({})",
          host,
          ProxyConfiguration.CONNECTOR_HTTP_NON_PROXY_HOSTS_ENV_VAR);
      return NO_PROXY;
    }

    String protocol = ProtocolNormalizer.normalize(uri.getScheme());
    return proxyConfiguration
        .getProxyDetails(protocol)
        .map(
            details -> {
              Proxy proxy =
                  new Proxy(Proxy.Type.HTTP, new InetSocketAddress(details.host(), details.port()));
              LOG.debug("Using proxy for target host [{}] => [{}]", host, proxy);
              return List.of(proxy);
            })
        .orElse(NO_PROXY);
  }

  @Override
  public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
    LOG.warn("Proxy connection to [{}] via [{}] failed: {}", uri, sa, ioe.getMessage());
  }
}
