/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.transport;

import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provider-neutral proxy resolution for OkHttp-based vendor SDKs (anthropic-java, openai-java),
 * which accept a {@link Proxy} rather than a pre-built HTTP client. Built directly on top of the
 * shared {@link ProxyConfiguration} building block (the same one {@code AgenticAiHttpProxySupport}
 * exposes) so it stays framework-neutral -- no {@code dev.langchain4j.*} dependency, and no
 * AWS/Azure/JDK-client concerns that only the LangChain4j-backed providers need (see {@code
 * ChatModelHttpProxySupport} in the {@code langchain4j} package for those).
 */
public class HttpTransportSupport {
  private static final Logger LOG = LoggerFactory.getLogger(HttpTransportSupport.class);

  private final ProxyConfiguration proxyConfiguration;

  public HttpTransportSupport(ProxyConfiguration proxyConfiguration) {
    this.proxyConfiguration = proxyConfiguration;
  }

  /**
   * Returns the proxy configured for the target scheme, if any, together with any credentials for
   * the SDK's own proxy authenticator. Shared so provider-specific chat model implementations
   * (Anthropic today, OpenAI later) can reuse it unchanged.
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
