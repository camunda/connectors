/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.connector.http.client.client.jdk.proxy.JdkHttpClientProxyConfigurator;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.http.ExecutableHttpRequest;
import software.amazon.awssdk.http.HttpExecuteRequest;
import software.amazon.awssdk.http.HttpExecuteResponse;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;

/**
 * End-to-end style timeout test for the Apache HTTP client returned by {@link
 * ChatModelHttpProxySupport#createAwsHttpClientBuilder(URI)} after a chat model provider applies
 * its timeouts.
 *
 * <p>Spins up a real WireMock server that delays its response well beyond the configured socket
 * timeout, then asserts the Apache HTTP client (used by Bedrock) respects the caller-configured
 * socket timeout instead of falling back to the AWS SDK default of 30s.
 *
 * <p>Regression test for <a href="https://github.com/camunda/connectors/issues/7193">issue
 * #7193</a> where Bedrock calls were being killed by Apache's default 30s socket timeout even
 * though the connector-level API timeout was set to several minutes.
 */
@WireMockTest
class ChatModelHttpProxySupportTimeoutE2ETest {

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration SHORT_SOCKET_TIMEOUT = Duration.ofSeconds(1);
  private static final Duration GENEROUS_SOCKET_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration SIMULATED_LLM_DELAY = Duration.ofSeconds(3);

  private final ChatModelHttpProxySupport proxySupport =
      new ChatModelHttpProxySupport(
          ProxyConfiguration.NONE, new JdkHttpClientProxyConfigurator(ProxyConfiguration.NONE));

  @Test
  void apacheHttpClientShouldRespectCallerConfiguredSocketTimeout(WireMockRuntimeInfo wm) {
    // given — WireMock simulates a slow LLM endpoint, longer than the configured socket timeout
    stubFor(
        any(anyUrl())
            .willReturn(
                aResponse().withStatus(200).withFixedDelay((int) SIMULATED_LLM_DELAY.toMillis())));

    // simulates what BedrockChatModelProvider does: derive the API timeout and apply it to the
    // builder returned by ChatModelHttpProxySupport
    try (SdkHttpClient client =
        proxySupport
            .createAwsHttpClientBuilder(URI.create(wm.getHttpBaseUrl()))
            .connectionTimeout(CONNECT_TIMEOUT)
            .socketTimeout(SHORT_SOCKET_TIMEOUT)
            .build()) {
      // when / then — request should fail close to the configured socket timeout, not the AWS
      // default of 30s, and well before the simulated LLM response delay
      final var start = Instant.now();
      assertThatThrownBy(() -> executeAwsRequest(client, wm.getHttpBaseUrl()))
          .isInstanceOf(IOException.class);

      final var elapsed = Duration.between(start, Instant.now());
      assertThat(elapsed)
          .as("socket timeout should fire close to the configured value")
          .isLessThan(SIMULATED_LLM_DELAY);
      assertThat(elapsed).isGreaterThanOrEqualTo(SHORT_SOCKET_TIMEOUT);
    }
  }

  @Test
  void apacheHttpClientShouldCompleteWhenSocketTimeoutIsLargerThanResponseDelay(
      WireMockRuntimeInfo wm) throws Exception {
    // given — same delay but a socket timeout that is long enough to accommodate it
    stubFor(
        any(anyUrl())
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody("ok")
                    .withFixedDelay((int) SIMULATED_LLM_DELAY.toMillis())));

    try (SdkHttpClient client =
        proxySupport
            .createAwsHttpClientBuilder(URI.create(wm.getHttpBaseUrl()))
            .connectionTimeout(CONNECT_TIMEOUT)
            .socketTimeout(GENEROUS_SOCKET_TIMEOUT)
            .build()) {
      // when
      final HttpExecuteResponse response = executeAwsRequest(client, wm.getHttpBaseUrl());

      // then
      assertThat(response.httpResponse().statusCode()).isEqualTo(200);
    }
  }

  private static HttpExecuteResponse executeAwsRequest(SdkHttpClient client, String baseUrl)
      throws IOException {
    final var sdkRequest =
        SdkHttpFullRequest.builder()
            .method(SdkHttpMethod.POST)
            .uri(URI.create(baseUrl + "/llm/invoke"))
            .putHeader("Content-Type", "application/json")
            .build();
    final ExecutableHttpRequest call =
        client.prepareRequest(HttpExecuteRequest.builder().request(sdkRequest).build());
    return call.call();
  }
}
