/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.common;

import io.camunda.connector.http.client.client.jdk.proxy.JdkHttpClientProxyConfigurator;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides HTTP proxy support utilities for Agentic AI connectors. One shared adapter per HTTP
 * client shape a native provider's vendor SDK needs (JDK {@code HttpClient} today, {@code
 * java.net.Proxy} for OkHttp-based SDKs); add another adapter method here rather than a new class
 * when the next shape is needed (e.g. an AWS-SDK proxy configuration for a native Bedrock
 * provider).
 */
public class AgenticAiHttpProxySupport {
  private static final Logger LOG = LoggerFactory.getLogger(AgenticAiHttpProxySupport.class);

  private final ProxyConfiguration proxyConfiguration;
  private final JdkHttpClientProxyConfigurator jdkHttpClientProxyConfigurator;

  public AgenticAiHttpProxySupport(ProxyConfiguration proxyConfiguration) {
    this.proxyConfiguration = proxyConfiguration;
    this.jdkHttpClientProxyConfigurator = new JdkHttpClientProxyConfigurator(proxyConfiguration);
  }

  public ProxyConfiguration getProxyConfiguration() {
    return proxyConfiguration;
  }

  public JdkHttpClientProxyConfigurator getJdkHttpClientProxyConfigurator() {
    return jdkHttpClientProxyConfigurator;
  }

  /**
   * Returns the proxy configured for the target scheme, if any, together with any credentials for
   * an OkHttp-based SDK's own proxy authenticator (e.g. anthropic-java, openai-java, which accept a
   * {@link Proxy} rather than a pre-built HTTP client).
   */
  public Optional<OkHttpProxy> okHttpProxy(String scheme) {
    return proxyConfiguration
        .getProxyDetails(scheme)
        .map(
            proxyDetails -> {
              LOG.debug(
                  "Using proxy for target scheme [{}] => [{}:{}]",
                  scheme,
                  proxyDetails.host(),
                  proxyDetails.port());
              final var proxy =
                  new Proxy(
                      Proxy.Type.HTTP,
                      new InetSocketAddress(proxyDetails.host(), proxyDetails.port()));
              return proxyDetails.hasCredentials()
                  ? new OkHttpProxy(proxy, proxyDetails.user(), proxyDetails.password())
                  : new OkHttpProxy(proxy, null, null);
            });
  }

  /** Proxy plus optional credentials in a form neutral to any OkHttp-based SDK. */
  public record OkHttpProxy(Proxy proxy, @Nullable String username, @Nullable String password) {
    public boolean hasCredentials() {
      return username != null && !username.isBlank();
    }
  }
}
