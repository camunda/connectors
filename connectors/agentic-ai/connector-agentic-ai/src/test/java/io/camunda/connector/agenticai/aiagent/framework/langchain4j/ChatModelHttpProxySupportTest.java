/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.azure.core.http.ProxyOptions;
import io.camunda.connector.agenticai.aiagent.framework.transport.HttpTransportSupport;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.http.apache.ApacheHttpClient;

/**
 * {@link ChatModelHttpProxySupport} now only holds the langchain4j-specific {@link
 * ChatModelHttpProxySupport.CloseableJdkHttpClientBuilder} wrapper; the actual proxy-aware
 * client/builder construction lives in {@link HttpTransportSupport} (covered by {@code
 * HttpTransportSupportTest}). These tests assert that this class delegates correctly, not the
 * underlying proxy/AWS/Azure logic itself.
 */
@ExtendWith(MockitoExtension.class)
class ChatModelHttpProxySupportTest {

  private static final String HTTPS_ENDPOINT = "https://example.com";

  @Mock private HttpTransportSupport httpTransportSupport;

  private ChatModelHttpProxySupport proxySupport;

  @BeforeEach
  void setUp() {
    proxySupport = new ChatModelHttpProxySupport(httpTransportSupport);
  }

  @Nested
  class CreateJdkHttpClientBuilder {

    @Test
    void shouldWrapTheProxyConfiguredBuilderFromTransportSupport() {
      // given
      HttpClient.Builder configuredBuilder = HttpClient.newBuilder();
      when(httpTransportSupport.jdkHttpClientBuilder()).thenReturn(configuredBuilder);

      // when
      ChatModelHttpProxySupport.CloseableJdkHttpClientBuilder result =
          proxySupport.createJdkHttpClientBuilder();

      // then
      assertThat(result).isNotNull();
      verify(httpTransportSupport).jdkHttpClientBuilder();
    }

    @Test
    void shouldCloseTheClientBuiltByLangchain4j() {
      // given
      when(httpTransportSupport.jdkHttpClientBuilder()).thenReturn(HttpClient.newBuilder());
      ChatModelHttpProxySupport.CloseableJdkHttpClientBuilder result =
          proxySupport.createJdkHttpClientBuilder();

      // when
      var httpClient = result.build();

      // then (closing must not throw, even though the built client was already captured)
      assertThat(httpClient).isNotNull();
      result.close();
    }
  }

  @Nested
  class CreateAwsHttpClientBuilder {

    @Test
    void shouldDelegateToTransportSupport() {
      // given
      URI endpointOverride = URI.create("https://bedrock.amazonaws.com");
      ApacheHttpClient.Builder httpClientBuilder = mock(ApacheHttpClient.Builder.class);
      when(httpTransportSupport.awsHttpClientBuilder(endpointOverride))
          .thenReturn(httpClientBuilder);

      // when
      ApacheHttpClient.Builder result = proxySupport.createAwsHttpClientBuilder(endpointOverride);

      // then
      assertThat(result).isSameAs(httpClientBuilder);
      verify(httpTransportSupport).awsHttpClientBuilder(endpointOverride);
    }

    @Test
    void shouldDelegateToTransportSupportWithNullEndpoint() {
      // given
      ApacheHttpClient.Builder httpClientBuilder = mock(ApacheHttpClient.Builder.class);
      when(httpTransportSupport.awsHttpClientBuilder(any())).thenReturn(httpClientBuilder);

      // when
      ApacheHttpClient.Builder result = proxySupport.createAwsHttpClientBuilder(null);

      // then
      assertThat(result).isSameAs(httpClientBuilder);
      verify(httpTransportSupport).awsHttpClientBuilder(null);
    }
  }

  @Nested
  class CreateAzureProxyOptions {

    @Test
    void shouldDelegateToTransportSupport() {
      // given
      ProxyOptions proxyOptions =
          new ProxyOptions(
              ProxyOptions.Type.HTTP, new InetSocketAddress("proxy.example.com", 8080));
      when(httpTransportSupport.azureProxyOptions(HTTPS_ENDPOINT))
          .thenReturn(Optional.of(proxyOptions));

      // when
      Optional<ProxyOptions> result = proxySupport.createAzureProxyOptions(HTTPS_ENDPOINT);

      // then
      assertThat(result).containsSame(proxyOptions);
      verify(httpTransportSupport).azureProxyOptions(HTTPS_ENDPOINT);
    }

    @Test
    void shouldPropagateEmptyResultWhenNoProxyConfigured() {
      // given
      when(httpTransportSupport.azureProxyOptions(HTTPS_ENDPOINT)).thenReturn(Optional.empty());

      // when
      Optional<ProxyOptions> result = proxySupport.createAzureProxyOptions(HTTPS_ENDPOINT);

      // then
      assertThat(result).isEmpty();
    }
  }
}
