/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.camunda.connector.http.client.client.jdk.proxy.JdkHttpClientProxyConfigurator;
import java.net.http.HttpClient;
import org.junit.jupiter.api.Test;

class ChatModelHttpProxySupportTest {

  @Test
  void configuresTheHttpClientBuilderWithTheConnectorProxySettingsAndClosesTheBuiltClient() {
    final var proxyConfigurator = mock(JdkHttpClientProxyConfigurator.class);
    final var proxySupport = new ChatModelHttpProxySupport(proxyConfigurator);

    final var builder = proxySupport.createJdkHttpClientBuilder();

    verify(proxyConfigurator).configure(any(HttpClient.Builder.class));
    assertThat(builder.build()).isNotNull();
    builder.close();
  }
}
