/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.mistral;

import static io.camunda.connector.agenticai.aiagent.testsupport.AgentConfigurations.DEFAULT_RESPONSE;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.core.ObjectMappers;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import io.camunda.connector.agenticai.aiagent.api.conversation.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.chatmodel.connection.RequestCustomizations;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.OpenAiContentConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.CompletionsRequestSpec;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsRequestConverter;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.ProviderContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ReasoningContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.document.jackson.JacksonModuleDocumentSerializer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The OpenAI Chat Completions request converter wired with the Mistral chunk strategy and reasoning
 * dialect, as the Mistral provider wires it.
 */
class MistralCompletionsRequestConversionTest {

  private final ObjectMapper objectMapper =
      new ObjectMapper().registerModule(new JacksonModuleDocumentSerializer());
  private final OpenAiContentConverter contentConverter = new OpenAiContentConverter(objectMapper);
  private final OpenAiCompletionsRequestConverter mistralConverter =
      new OpenAiCompletionsRequestConverter(
          MistralConfiguration.PROVIDER,
          contentConverter,
          new MistralDocumentUrlContentChunkStrategy(objectMapper),
          objectMapper,
          new MistralReasoningContentDialect());

  private static final RequestCustomizations NO_CUSTOMIZATIONS =
      RequestCustomizations.of(null, null, null);

  private static JsonNode requestBodyAsJson(ChatCompletionCreateParams params) {
    return ObjectMappers.jsonMapper().valueToTree(params._body());
  }

  private static CompletionsRequestSpec spec() {
    return new CompletionsRequestSpec("gpt-4o", null, null, null, null, null, NO_CUSTOMIZATIONS);
  }

  @Test
  void replaysChunkedReasoningContentAlongsideTextAsChunkedAssistantContent() {
    // Mirrors what OpenAiCompletionsResponseConverter#toReasoningContent produces for a Mistral
    // reasoning-capable model's response: a ReasoningContent whose payload is shaped like a
    // stripped `thinking` chunk. It must be replayed byte-faithfully, in original order, ahead of
    // the plain text.
    final var snapshot =
        new ConversationSnapshot(
            List.of(
                AssistantMessage.builder()
                    .content(
                        List.of(
                            new ReasoningContent(
                                "mistral",
                                Map.of("type", "thinking", "closed", true),
                                "5+7=12",
                                null),
                            TextContent.textContent("The answer is 12.")))
                    .build()),
            List.of());

    final var params = mistralConverter.toRequest(spec(), DEFAULT_RESPONSE, snapshot);

    final var contentNode = requestBodyAsJson(params).path("messages").get(0).path("content");
    assertThat(contentNode.isArray()).isTrue();
    assertThat(contentNode.get(0).path("type").asText()).isEqualTo("thinking");
    assertThat(contentNode.get(0).path("closed").asBoolean()).isTrue();
    assertThat(contentNode.get(0).path("thinking").get(0).path("text").asText())
        .isEqualTo("5+7=12");
    assertThat(contentNode.get(1).path("type").asText()).isEqualTo("text");
    assertThat(contentNode.get(1).path("text").asText()).isEqualTo("The answer is 12.");
  }

  @Test
  void replaysAMultiItemThinkingArrayVerbatimInsteadOfCollapsingIt() {
    // OpenAiCompletionsResponseConverter leaves a non-reconstructible (multi-item) `thinking`
    // array in the payload rather than stripping it; this must be replayed exactly as-is, not
    // rebuilt from the single joined text() -- which would lose the second item.
    final var snapshot =
        new ConversationSnapshot(
            List.of(
                AssistantMessage.builder()
                    .content(
                        List.of(
                            new ReasoningContent(
                                "mistral",
                                Map.of(
                                    "type",
                                    "thinking",
                                    "thinking",
                                    List.of(
                                        Map.of("type", "text", "text", "5 + 7 "),
                                        Map.of("type", "text", "text", "is 12.")),
                                    "closed",
                                    true),
                                "5 + 7 is 12.",
                                null)))
                    .build()),
            List.of());

    final var params = mistralConverter.toRequest(spec(), DEFAULT_RESPONSE, snapshot);

    final var contentNode = requestBodyAsJson(params).path("messages").get(0).path("content");
    final var thinkingItems = contentNode.get(0).path("thinking");
    assertThat(thinkingItems).hasSize(2);
    assertThat(thinkingItems.get(0).path("text").asText()).isEqualTo("5 + 7 ");
    assertThat(thinkingItems.get(1).path("text").asText()).isEqualTo("is 12.");
  }

  @Test
  void doesNotOmitAssistantMessageWithOnlyChunkedReasoningContent() {
    // Unlike a non-replayable ReasoningContent, a chunked one must not be treated as "nothing
    // representable" even when it is the only content and there are no tool calls either.
    final var snapshot =
        new ConversationSnapshot(
            List.of(
                AssistantMessage.builder()
                    .content(
                        List.of(
                            new ReasoningContent(
                                "mistral", Map.of("type", "thinking"), "still thinking", null)))
                    .build()),
            List.of());

    final var params = mistralConverter.toRequest(spec(), DEFAULT_RESPONSE, snapshot);

    assertThat(params.messages()).hasSize(1);
    final var contentNode = requestBodyAsJson(params).path("messages").get(0).path("content");
    assertThat(contentNode.isArray()).isTrue();
    assertThat(contentNode).hasSize(1);
    assertThat(contentNode.get(0).path("type").asText()).isEqualTo("thinking");
  }

  @Test
  void dropsReasoningContentFromADifferentProviderEvenWhenPayloadShapeMatches() {
    // Anthropic's raw thinking-block payload keeps a type=thinking field after its own text
    // extraction (see AnthropicMessageResponseConverter#toReasoningContent), the same shape
    // Mistral's chunked reasoning uses. A provider-tag mismatch must still drop it instead of
    // misreplaying Anthropic reasoning content as a Mistral chunk after a provider switch.
    final var snapshot =
        new ConversationSnapshot(
            List.of(
                AssistantMessage.builder()
                    .content(
                        List.of(
                            new ReasoningContent(
                                "anthropic",
                                Map.of("type", "thinking", "signature", "sig-123"),
                                "anthropic reasoning text",
                                null),
                            TextContent.textContent("final answer")))
                    .build()),
            List.of());

    final var params = mistralConverter.toRequest(spec(), DEFAULT_RESPONSE, snapshot);

    final var assistant = params.messages().get(0).asAssistant();
    assertThat(assistant.content().orElseThrow().asText()).isEqualTo("final answer");
  }

  @Test
  void replaysAReplayableProviderContentInItsOriginalPositionAlongsideReasoning() {
    // Mirrors what OpenAiCompletionsResponseConverter#mapChunkedContent produces for a real
    // interleaved [thinking, reference, text] response: a same-provider, chunk-shaped
    // ProviderContent must be replayed verbatim in its original position, not dropped, and not
    // reordered relative to the reasoning/text entries around it.
    final var snapshot =
        new ConversationSnapshot(
            List.of(
                AssistantMessage.builder()
                    .content(
                        List.of(
                            new ReasoningContent(
                                "mistral", Map.of("type", "thinking"), "thought about it", null),
                            ProviderContent.providerContent(
                                "mistral",
                                Map.of("type", "reference", "reference_ids", List.of(1, 2))),
                            TextContent.textContent("See source above.")))
                    .build()),
            List.of());

    final var params = mistralConverter.toRequest(spec(), DEFAULT_RESPONSE, snapshot);

    final var contentNode = requestBodyAsJson(params).path("messages").get(0).path("content");
    assertThat(contentNode).hasSize(3);
    assertThat(contentNode.get(0).path("type").asText()).isEqualTo("thinking");
    assertThat(contentNode.get(1).path("type").asText()).isEqualTo("reference");
    assertThat(contentNode.get(1).path("reference_ids").get(0).asInt()).isEqualTo(1);
    assertThat(contentNode.get(1).path("reference_ids").get(1).asInt()).isEqualTo(2);
    assertThat(contentNode.get(2).path("type").asText()).isEqualTo("text");
    assertThat(contentNode.get(2).path("text").asText()).isEqualTo("See source above.");
  }

  @Test
  void dropsAProviderContentShapedLikeACustomToolCallInsteadOfReplayingItAsAChunk() {
    // OpenAiCompletionsResponseConverter#toToolCall also wraps a custom tool call as
    // ProviderContent, but its raw payload shape is unrelated to a content chunk and must never
    // be sent back as one -- only the explicit chunk-type allow-list makes it replayable.
    final var snapshot =
        new ConversationSnapshot(
            List.of(
                AssistantMessage.builder()
                    .content(
                        List.of(
                            ProviderContent.providerContent(
                                "mistral",
                                Map.of("type", "custom", "custom", Map.of("input", "x"))),
                            TextContent.textContent("final answer")))
                    .build()),
            List.of());

    final var params = mistralConverter.toRequest(spec(), DEFAULT_RESPONSE, snapshot);

    final var assistant = params.messages().get(0).asAssistant();
    assertThat(assistant.content().orElseThrow().asText()).isEqualTo("final answer");
  }

  @Test
  void dropsAProviderContentFromADifferentProviderEvenWhenChunkShaped() {
    final var snapshot =
        new ConversationSnapshot(
            List.of(
                AssistantMessage.builder()
                    .content(
                        List.of(
                            ProviderContent.providerContent(
                                "openai", Map.of("type", "reference", "reference_ids", List.of(1))),
                            TextContent.textContent("final answer")))
                    .build()),
            List.of());

    final var params = mistralConverter.toRequest(spec(), DEFAULT_RESPONSE, snapshot);

    final var assistant = params.messages().get(0).asAssistant();
    assertThat(assistant.content().orElseThrow().asText()).isEqualTo("final answer");
  }
}
