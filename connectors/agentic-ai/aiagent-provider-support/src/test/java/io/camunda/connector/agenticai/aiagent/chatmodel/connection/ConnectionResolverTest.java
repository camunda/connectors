/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.common.http.AgenticAiHttpProxySupport;
import io.camunda.connector.agenticai.common.http.AgenticAiHttpProxySupport.OkHttpProxy;
import io.camunda.connector.http.client.authentication.OAuthClientCredentialsTokenResolver;
import io.camunda.connector.http.client.authentication.OAuthConstants;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ConnectionResolverTest {

  private static final OkHttpProxy PROXY =
      new OkHttpProxy(
          new Proxy(Proxy.Type.HTTP, new InetSocketAddress("proxy.example", 3128)), "user", "pw");

  private final AgenticAiHttpProxySupport proxySupport = mock(AgenticAiHttpProxySupport.class);
  private final OAuthClientCredentialsTokenResolver tokenResolver =
      mock(OAuthClientCredentialsTokenResolver.class);
  private final ConnectionResolver resolver = new ConnectionResolver(proxySupport, tokenResolver);

  private final String previousNonProxyHosts = System.getProperty("http.nonProxyHosts");

  @AfterEach
  void restoreNonProxyHosts() {
    if (previousNonProxyHosts == null) {
      System.clearProperty("http.nonProxyHosts");
    } else {
      System.setProperty("http.nonProxyHosts", previousNonProxyHosts);
    }
  }

  @Test
  void keepsTheTimeoutAndBoundsTheConnectTimeoutByIt() {
    when(proxySupport.okHttpProxy(any())).thenReturn(Optional.empty());

    final var long1 = resolver.resolve(Duration.ofMinutes(3), null, "https://api.example");
    assertThat(long1.timeout()).isEqualTo(Duration.ofMinutes(3));
    assertThat(long1.connectTimeout()).isEqualTo(ConnectionResolver.CONNECT_TIMEOUT);

    final var short1 = resolver.resolve(Duration.ofSeconds(5), null, "https://api.example");
    assertThat(short1.timeout()).isEqualTo(Duration.ofSeconds(5));
    assertThat(short1.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
  }

  @Test
  void resolvesTheProxyForTheSchemeOfTheConfiguredEndpoint() {
    when(proxySupport.okHttpProxy(ProxyConfiguration.SCHEME_HTTP)).thenReturn(Optional.of(PROXY));

    final var settings =
        resolver.resolve(Duration.ofMinutes(1), "http://llm.example:8080/v1", "https://default");

    assertThat(settings.proxy()).contains(PROXY);
  }

  @Test
  void resolvesTheProxyForTheDefaultEndpointWithoutAConfiguredOne() {
    when(proxySupport.okHttpProxy(ProxyConfiguration.SCHEME_HTTPS)).thenReturn(Optional.of(PROXY));

    final var settings = resolver.resolve(Duration.ofMinutes(1), null, "https://api.example");

    assertThat(settings.proxy()).contains(PROXY);
  }

  @Test
  void skipsTheProxyForANonProxyHost() {
    System.setProperty("http.nonProxyHosts", "*.internal.example|localhost");

    final var configured =
        resolver.resolve(Duration.ofMinutes(1), "https://llm.internal.example", "https://default");
    final var byDefault =
        resolver.resolve(Duration.ofMinutes(1), null, "https://models.internal.example");

    assertThat(configured.proxy()).isEmpty();
    assertThat(byDefault.proxy()).isEmpty();
    verify(proxySupport, never()).okHttpProxy(any());
  }

  @Test
  void requestsTheOAuthTokenForEveryCall() {
    when(tokenResolver.resolveAccessToken(
            "https://token", "client", "secret", "aud", OAuthConstants.BASIC_AUTH_HEADER, "read"))
        .thenReturn("token-1", "token-2");

    final var bearerToken =
        resolver.bearerToken(
            new EndpointAuthentication.OAuthClientCredentials(
                "https://token",
                "client",
                "secret",
                "aud",
                EndpointAuthentication.ClientAuthenticationMethod.BASIC_AUTH_HEADER,
                "read"));

    assertThat(bearerToken.get()).isEqualTo("token-1");
    assertThat(bearerToken.get()).isEqualTo("token-2");
  }
}
