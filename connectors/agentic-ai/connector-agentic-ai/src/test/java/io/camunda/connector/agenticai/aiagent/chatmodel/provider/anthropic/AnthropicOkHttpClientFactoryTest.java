/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicApiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicCompatibleBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.shared.CompatibleAuthentication.CompatibleApiKeyAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.v2.shared.CompatibleAuthentication.CompatibleNoAuthentication;
import io.camunda.connector.agenticai.aiagent.transport.HttpTransportSupport;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link AnthropicOkHttpClientFactory} at the wire level: the built {@link
 * AnthropicClient} issues a real (WireMock-backed) request, and assertions verify what actually
 * went over the wire rather than reflecting into SDK internals.
 */
@WireMockTest
class AnthropicOkHttpClientFactoryTest {

  private static final String SAMPLE_MESSAGE_RESPONSE =
      """
      {
        "id": "msg_test",
        "model": "claude-sonnet-4-6",
        "role": "assistant",
        "type": "message",
        "content": [{"type": "text", "text": "hi"}],
        "stop_reason": "end_turn",
        "usage": {"input_tokens": 1, "output_tokens": 1}
      }
      """;

  /** Mirrors {@code AnthropicOkHttpClientFactory}'s no-auth sentinel api key. */
  private static final String NO_AUTH_SENTINEL_API_KEY = "not-required";

  private final HttpTransportSupport transport = mock(HttpTransportSupport.class);

  @BeforeEach
  void setUp() {
    when(transport.okHttpProxy(anyString())).thenReturn(Optional.empty());
    stubFor(post(urlPathEqualTo("/v1/messages")).willReturn(okJson(SAMPLE_MESSAGE_RESPONSE)));
  }

  @Test
  void directBackendSendsConfiguredApiKey(WireMockRuntimeInfo wireMock) {
    var backend = new AnthropicApiBackend("direct-secret-key");
    AnthropicClient client = new AnthropicOkHttpClientFactory(backend, null, transport).create();

    // the direct backend always targets the production Anthropic base URL; redirect this one
    // instance to the WireMock server while keeping its resolved api key credential intact.
    AnthropicClient redirected =
        client.withOptions(options -> options.baseUrl(wireMock.getHttpBaseUrl()));
    redirected.messages().create(minimalMessageParams());

    verify(
        postRequestedFor(urlPathEqualTo("/v1/messages"))
            .withHeader("x-api-key", equalTo("direct-secret-key")));
  }

  @Test
  void compatibleBackendUsesEndpointAndApiKeyAuthentication(WireMockRuntimeInfo wireMock) {
    var backend =
        new AnthropicCompatibleBackend(
            wireMock.getHttpBaseUrl(),
            Map.of("X-Custom-Header", "custom-value"),
            Map.of("api-version", "2026-01-01"),
            null,
            new CompatibleApiKeyAuthentication("compatible-secret-key"));

    var client = new AnthropicOkHttpClientFactory(backend, null, transport).create();
    client.messages().create(minimalMessageParams());

    verify(
        postRequestedFor(urlPathEqualTo("/v1/messages"))
            .withHeader("x-api-key", equalTo("compatible-secret-key"))
            .withHeader("X-Custom-Header", equalTo("custom-value"))
            .withQueryParam("api-version", equalTo("2026-01-01")));
  }

  @Test
  void compatibleBackendWithNoAuthenticationUsesSentinelApiKey(WireMockRuntimeInfo wireMock) {
    var backend =
        new AnthropicCompatibleBackend(
            wireMock.getHttpBaseUrl(), null, null, null, new CompatibleNoAuthentication());

    var client = new AnthropicOkHttpClientFactory(backend, null, transport).create();
    client.messages().create(minimalMessageParams());

    verify(
        postRequestedFor(urlPathEqualTo("/v1/messages"))
            .withHeader("x-api-key", equalTo(NO_AUTH_SENTINEL_API_KEY)));
  }

  private static MessageCreateParams minimalMessageParams() {
    return MessageCreateParams.builder()
        .model("claude-sonnet-4-6")
        .maxTokens(16)
        .addUserMessage("hi")
        .build();
  }
}
