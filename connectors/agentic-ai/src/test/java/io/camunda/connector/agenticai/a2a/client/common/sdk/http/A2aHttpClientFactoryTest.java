/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.common.sdk.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.a2a.client.http.A2AHttpClient;
import io.camunda.connector.agenticai.common.AgenticAiHttpProxySupport;
import io.camunda.connector.http.client.client.jdk.proxy.JdkHttpClientProxyConfigurator;
import java.net.http.HttpClient;
import org.junit.jupiter.api.Test;

class A2aHttpClientFactoryTest {

  @Test
  void createsHttpClientWithProxyConfiguration() {
    var proxySupport = mock(AgenticAiHttpProxySupport.class);
    var proxyConfigurator = mock(JdkHttpClientProxyConfigurator.class);
    when(proxySupport.getJdkHttpClientProxyConfigurator()).thenReturn(proxyConfigurator);
    when(proxyConfigurator.configure(any(HttpClient.Builder.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var factory = new A2aHttpClientFactory(proxySupport);
    A2AHttpClient httpClient = factory.createHttpClient();

    assertThat(httpClient).isNotNull().isInstanceOf(CustomJdkA2AHttpClient.class);
    verify(proxyConfigurator).configure(any(HttpClient.Builder.class));
  }
}
