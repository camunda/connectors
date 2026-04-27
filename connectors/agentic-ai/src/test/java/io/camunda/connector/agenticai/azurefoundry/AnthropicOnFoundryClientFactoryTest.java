/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.azurefoundry;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.ChatModelHttpProxySupport;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.jsonschema.JsonSchemaConverter;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureFoundryProviderConfiguration.AzureAiFoundryModel.AnthropicModel;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureFoundryProviderConfiguration.AzureAiFoundryModel.AnthropicModel.AnthropicModelParameters;
import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.AzureAuthentication.AzureApiKeyAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.AzureAuthentication.AzureClientCredentialsAuthentication;
import io.camunda.connector.agenticai.azurefoundry.langchain4j.AnthropicOnFoundryChatModel;
import io.camunda.connector.http.client.client.jdk.proxy.JdkHttpClientProxyConfigurator;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import java.time.Duration;
import org.junit.jupiter.api.Test;

@WireMockTest
class AnthropicOnFoundryClientFactoryTest {

  private final ChatModelHttpProxySupport proxySupport =
      new ChatModelHttpProxySupport(
          ProxyConfiguration.NONE, new JdkHttpClientProxyConfigurator(ProxyConfiguration.NONE));

  private final AnthropicOnFoundryClientFactory factory =
      new AnthropicOnFoundryClientFactory(
          proxySupport, new JsonSchemaConverter(new ObjectMapper()));

  @Test
  void buildsAnthropicChatModelWithApiKeyAuth() {
    AnthropicOnFoundryChatModel chatModel =
        factory.create(
            "https://my-resource.services.ai.azure.com",
            new AzureApiKeyAuthentication("api-key-value"),
            Duration.ofSeconds(30),
            new AnthropicModel(
                "claude-sonnet-4-6", new AnthropicModelParameters(1024, 0.7, 0.9, 40)));

    assertThat(chatModel).isNotNull();
    assertThat(chatModel.modelConfig().deploymentName()).isEqualTo("claude-sonnet-4-6");
  }

  @Test
  void buildsChatModelEvenWhenEndpointHasTrailingSlash() {
    AnthropicOnFoundryChatModel chatModel =
        factory.create(
            "https://my-resource.services.ai.azure.com/",
            new AzureApiKeyAuthentication("api-key"),
            null,
            new AnthropicModel("claude-sonnet-4-6", null));

    assertThat(chatModel).isNotNull();
  }

  @Test
  void buildsAnthropicChatModelWithClientCredentialsAuth() {
    AnthropicOnFoundryChatModel chatModel =
        factory.create(
            "https://my-resource.services.ai.azure.com",
            new AzureClientCredentialsAuthentication(
                "client-id", "client-secret", "tenant-id", null),
            null,
            new AnthropicModel("claude-sonnet-4-6", null));

    // Bearer-token supplier construction does not hit Azure until the first request is made.
    assertThat(chatModel).isNotNull();
  }

  @Test
  void usesCustomAuthorityHostWhenProvided() {
    AnthropicOnFoundryChatModel chatModel =
        factory.create(
            "https://my-resource.services.ai.azure.com",
            new AzureClientCredentialsAuthentication(
                "client-id", "client-secret", "tenant-id", "https://login.microsoftonline.us"),
            null,
            new AnthropicModel("claude-sonnet-4-6", null));

    assertThat(chatModel).isNotNull();
  }

  // ---------------------------------------------------------------------------
  // WireMock round-trip tests — verify HTTP-level behaviour of the full chain
  // ---------------------------------------------------------------------------

  private static final String MINIMAL_SUCCESS_RESPONSE =
      """
      {
        "id": "msg_01",
        "type": "message",
        "role": "assistant",
        "model": "claude-sonnet-4-6",
        "content": [{"type": "text", "text": "ok"}],
        "stop_reason": "end_turn",
        "usage": {"input_tokens": 1, "output_tokens": 1}
      }
      """;

  @Test
  void normalizesTrailingSlashInEndpoint(WireMockRuntimeInfo wm) throws Exception {
    stubFor(
        post(urlEqualTo("/anthropic/v1/messages"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(MINIMAL_SUCCESS_RESPONSE)));

    AnthropicOnFoundryChatModel chatModel =
        factory.create(
            wm.getHttpBaseUrl() + "/", // trailing slash — must be stripped
            new AzureApiKeyAuthentication("test-key"),
            Duration.ofSeconds(30),
            new AnthropicModel(
                "claude-sonnet-4-6", new AnthropicModelParameters(100, null, null, null)));

    // Drive the call through the Anthropic SDK client directly (no langchain4j) to stay within
    // the azurefoundry package ArchUnit boundary.
    triggerMessages(chatModel);

    // Request must arrive at exactly /anthropic/v1/messages — no double slash
    verify(postRequestedFor(urlEqualTo("/anthropic/v1/messages")));
  }

  @Test
  void injectsApiKeyHeader(WireMockRuntimeInfo wm) throws Exception {
    stubFor(
        post(urlEqualTo("/anthropic/v1/messages"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(MINIMAL_SUCCESS_RESPONSE)));

    AnthropicOnFoundryChatModel chatModel =
        factory.create(
            wm.getHttpBaseUrl(),
            new AzureApiKeyAuthentication("secret-key-value"),
            Duration.ofSeconds(30),
            new AnthropicModel(
                "claude-sonnet-4-6", new AnthropicModelParameters(100, null, null, null)));

    triggerMessages(chatModel);

    // The Foundry SDK uses "x-api-key" (HEADER_API_KEY constant in FoundryBackend) for API-key
    // auth — same header name as direct Anthropic, not the "api-key" Azure OpenAI convention.
    verify(
        postRequestedFor(urlEqualTo("/anthropic/v1/messages"))
            .withHeader("x-api-key", equalTo("secret-key-value")));
  }

  /**
   * Extracts the {@code AnthropicClient} wired by the factory and makes one minimal messages call.
   * Uses reflection so the test stays in the {@code azurefoundry} package without importing
   * langchain4j types — which would violate the ArchUnit rule that restricts langchain4j usage to
   * the {@code azurefoundry.langchain4j} sub-package.
   */
  private static void triggerMessages(AnthropicOnFoundryChatModel chatModel) throws Exception {
    var clientField = AnthropicOnFoundryChatModel.class.getDeclaredField("client");
    clientField.setAccessible(true);
    AnthropicClient client = (AnthropicClient) clientField.get(chatModel);

    MessageCreateParams params =
        MessageCreateParams.builder()
            .model("claude-sonnet-4-6")
            .maxTokens(10L)
            .addUserMessage("hi")
            .build();
    client.messages().create(params);
  }
}
