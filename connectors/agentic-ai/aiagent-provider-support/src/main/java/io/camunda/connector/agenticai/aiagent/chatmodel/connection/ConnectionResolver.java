/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.connection;

import io.camunda.connector.agenticai.common.http.AgenticAiHttpProxySupport;
import io.camunda.connector.agenticai.common.http.AgenticAiHttpProxySupport.OkHttpProxy;
import io.camunda.connector.http.client.authentication.OAuthClientCredentialsTokenResolver;
import io.camunda.connector.http.client.proxy.NonProxyHosts;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Resolves the connection settings and the OAuth bearer tokens of the chat model providers. A
 * provider factory reads them from here and does not resolve timeouts, proxies or tokens by itself.
 */
public class ConnectionResolver {

  /**
   * TCP connect timeout applied to every chat model HTTP client. Kept short and constant: a healthy
   * public LLM endpoint accepts connections in well under a second, and anything in the "seconds"
   * range indicates DNS, firewall or proxy misconfiguration rather than model latency.
   */
  public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);

  private final AgenticAiHttpProxySupport httpProxySupport;
  private final OAuthClientCredentialsTokenResolver oAuthClientCredentialsTokenResolver;

  public ConnectionResolver(
      AgenticAiHttpProxySupport httpProxySupport,
      OAuthClientCredentialsTokenResolver oAuthClientCredentialsTokenResolver) {
    this.httpProxySupport = httpProxySupport;
    this.oAuthClientCredentialsTokenResolver = oAuthClientCredentialsTokenResolver;
  }

  /**
   * @param timeout timeout of a model call
   * @param endpoint the configured endpoint, or {@code null} to connect to {@code defaultEndpoint}
   * @param defaultEndpoint the endpoint of the provider the SDK connects to without a configured
   *     one, which decides about the proxy
   */
  public ConnectionSettings resolve(
      Duration timeout, @Nullable String endpoint, String defaultEndpoint) {
    final var target = URI.create(endpoint != null ? endpoint : defaultEndpoint);
    final var proxy =
        NonProxyHosts.isNonProxyHost(target.getHost())
            ? Optional.<OkHttpProxy>empty()
            : httpProxySupport.okHttpProxy(
                target.getScheme() != null ? target.getScheme() : ProxyConfiguration.SCHEME_HTTPS);
    return new ConnectionSettings(
        timeout, timeout.compareTo(CONNECT_TIMEOUT) < 0 ? timeout : CONNECT_TIMEOUT, proxy);
  }

  /** The access token of an OAuth 2.0 client credentials flow, requested for every call. */
  public Supplier<String> bearerToken(EndpointAuthentication.OAuthClientCredentials oauth) {
    return () ->
        oAuthClientCredentialsTokenResolver.resolveAccessToken(
            oauth.tokenEndpoint(),
            oauth.clientId(),
            oauth.clientSecret(),
            oauth.audience(),
            oauth.clientAuthentication().oauthConstant(),
            oauth.scopes());
  }
}
