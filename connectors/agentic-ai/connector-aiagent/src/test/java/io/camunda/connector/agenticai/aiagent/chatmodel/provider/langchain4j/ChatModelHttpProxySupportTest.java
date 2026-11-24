/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.http.client.client.jdk.proxy.JdkHttpClientProxyConfigurator;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import io.camunda.connector.http.client.proxy.ProxyConfiguration.ProxyDetails;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ChatModelHttpProxySupportTest {

  private static final URI TARGET = URI.create("https://llm.example.com/v1");

  @Test
  void buildsAClientThatRoutesThroughTheConfiguredProxy() {
    final var proxyConfiguration =
        (ProxyConfiguration)
            scheme -> Optional.of(new ProxyDetails(scheme, "proxy.example.com", 3128, null, null));
    final var proxySupport =
        new ChatModelHttpProxySupport(new JdkHttpClientProxyConfigurator(proxyConfiguration));

    try (final var builder = proxySupport.createJdkHttpClientBuilder()) {
      final var client = builder.httpClientBuilder().build();

      assertThat(client.proxy())
          .hasValueSatisfying(selector ->
              assertThat(selector.select(TARGET))
                  .containsExactly(
                      new Proxy(
                          Proxy.Type.HTTP, new InetSocketAddress("proxy.example.com", 3128))));
    }
  }

  @Test
  void buildsAClientWithoutProxyWhenNoProxyIsConfigured() {
    final var proxySupport =
        new ChatModelHttpProxySupport(new JdkHttpClientProxyConfigurator(ProxyConfiguration.NONE));

    try (final var builder = proxySupport.createJdkHttpClientBuilder()) {
      assertThat(builder.httpClientBuilder().build().proxy()).isEmpty();
    }
  }
}
