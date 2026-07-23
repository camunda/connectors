/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.transport;

import static io.camunda.connector.http.client.proxy.ProxyConfiguration.SCHEME_HTTP;
import static io.camunda.connector.http.client.proxy.ProxyConfiguration.SCHEME_HTTPS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import io.camunda.connector.http.client.proxy.ProxyConfiguration.ProxyDetails;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HttpTransportSupportTest {

  private static final String PROXY_HOST = "proxy.example.com";
  private static final int PROXY_PORT = 8080;
  private static final String PROXY_USER = "proxyuser";
  private static final String PROXY_PASSWORD = "proxypass";

  @Mock private ProxyConfiguration proxyConfiguration;

  private HttpTransportSupport transportSupport;

  @BeforeEach
  void setUp() {
    transportSupport = new HttpTransportSupport(proxyConfiguration);
  }

  @Test
  void returnsEmptyWhenNoProxyConfiguredForScheme() {
    when(proxyConfiguration.getProxyDetails(SCHEME_HTTPS)).thenReturn(Optional.empty());

    Optional<HttpTransportSupport.OkHttpProxy> result = transportSupport.okHttpProxy(SCHEME_HTTPS);

    assertThat(result).isEmpty();
  }

  @Test
  void derivesProxyWithoutCredentials() {
    var proxyDetails = new ProxyDetails(SCHEME_HTTPS, PROXY_HOST, PROXY_PORT, null, null);
    when(proxyConfiguration.getProxyDetails(SCHEME_HTTPS)).thenReturn(Optional.of(proxyDetails));

    Optional<HttpTransportSupport.OkHttpProxy> result = transportSupport.okHttpProxy(SCHEME_HTTPS);

    assertThat(result).isPresent();
    HttpTransportSupport.OkHttpProxy okHttpProxy = result.get();
    assertThat(okHttpProxy.proxy())
        .isEqualTo(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(PROXY_HOST, PROXY_PORT)));
    assertThat(okHttpProxy.hasCredentials()).isFalse();
    assertThat(okHttpProxy.username()).isNull();
    assertThat(okHttpProxy.password()).isNull();
  }

  @Test
  void derivesProxyWithCredentials() {
    var proxyDetails =
        new ProxyDetails(SCHEME_HTTPS, PROXY_HOST, PROXY_PORT, PROXY_USER, PROXY_PASSWORD);
    when(proxyConfiguration.getProxyDetails(SCHEME_HTTPS)).thenReturn(Optional.of(proxyDetails));

    Optional<HttpTransportSupport.OkHttpProxy> result = transportSupport.okHttpProxy(SCHEME_HTTPS);

    assertThat(result).isPresent();
    HttpTransportSupport.OkHttpProxy okHttpProxy = result.get();
    assertThat(okHttpProxy.proxy())
        .isEqualTo(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(PROXY_HOST, PROXY_PORT)));
    assertThat(okHttpProxy.hasCredentials()).isTrue();
    assertThat(okHttpProxy.username()).isEqualTo(PROXY_USER);
    assertThat(okHttpProxy.password()).isEqualTo(PROXY_PASSWORD);
  }

  @Test
  void queriesProxyDetailsForTheRequestedSchemeOnly() {
    var httpsProxyDetails = new ProxyDetails(SCHEME_HTTPS, PROXY_HOST, PROXY_PORT, null, null);
    when(proxyConfiguration.getProxyDetails(SCHEME_HTTPS))
        .thenReturn(Optional.of(httpsProxyDetails));
    when(proxyConfiguration.getProxyDetails(SCHEME_HTTP)).thenReturn(Optional.empty());

    assertThat(transportSupport.okHttpProxy(SCHEME_HTTP)).isEmpty();
    assertThat(transportSupport.okHttpProxy(SCHEME_HTTPS)).isPresent();
  }
}
