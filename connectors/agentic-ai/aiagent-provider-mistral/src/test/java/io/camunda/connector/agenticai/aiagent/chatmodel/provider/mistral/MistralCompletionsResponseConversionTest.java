/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.mistral;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.core.ObjectMappers;
import com.openai.models.chat.completions.ChatCompletion;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatResult;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsContentDialect;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsResponseConverter;
import io.camunda.connector.agenticai.aiagent.model.message.content.ProviderContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ReasoningContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

/**
 * The OpenAI Chat Completions response converter wired with the Mistral reasoning dialect, as the
 * Mistral provider wires it.
 */
class MistralCompletionsResponseConversionTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final OpenAiCompletionsResponseConverter converter =
      new OpenAiCompletionsResponseConverter(
          "openai", objectMapper, OpenAiCompletionsContentDialect.none());
  private final OpenAiCompletionsResponseConverter mistralConverter =
      new OpenAiCompletionsResponseConverter(
          MistralConfiguration.PROVIDER, objectMapper, new MistralReasoningContentDialect());

  private static ChatCompletion completionFromJson(String json) {
    try {
      return ObjectMappers.jsonMapper().readValue(json, ChatCompletion.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to parse test fixture JSON", e);
    }
  }

  private static ChatCompletion baseCompletion(String messageJson) {
    return baseCompletion(messageJson, "null");
  }

  private static ChatCompletion baseCompletion(String messageJson, String usageJson) {
    return completionWithFinishReason("stop", messageJson, usageJson);
  }

  private static ChatCompletion completionWithFinishReason(
      String finishReason, String messageJson) {
    return completionWithFinishReason(finishReason, messageJson, "null");
  }

  private static ChatCompletion completionWithFinishReason(
      String finishReason, String messageJson, String usageJson) {
    return completionFromJson(
        """
        {
          "id": "chatcmpl_123",
          "object": "chat.completion",
          "created": 0,
          "model": "gpt-4o",
          "choices": [
            {
              "index": 0,
              "finish_reason": "%s",
              "message": %s
            }
          ],
          "usage": %s
        }
        """
            .formatted(finishReason, messageJson, usageJson));
  }

  @Test
  void mapsChunkedThinkingAndTextContentToReasoningAndTextContent() {
    // Mirrors a real Mistral non-streaming response: content is an array of typed chunks instead
    // of a plain string, self-detected from the raw JSON shape (see
    // OpenAiCompletionsResponseConverter#mapContent); the thinking chunk itself is only recognized
    // by MistralReasoningContentDialect.
    final ChatCompletion completion =
        baseCompletion(
            """
            {
              "role": "assistant",
              "content": [
                {
                  "type": "thinking",
                  "thinking": [{"type": "text", "text": "5 + 7 is 12."}],
                  "closed": true
                },
                {"type": "text", "text": "The answer is 12."}
              ]
            }
            """);

    final ChatResult result = mistralConverter.toResult(completion);

    assertThat(result.assistantMessage().content())
        .containsExactly(
            new ReasoningContent(
                "mistral", Map.of("type", "thinking", "closed", true), "5 + 7 is 12.", null),
            TextContent.textContent("The answer is 12."));
  }

  @Test
  void mapsChunkedThinkingOnlyContentWithoutTrailingTextChunk() {
    final ChatCompletion completion =
        completionWithFinishReason(
            "length",
            """
            {
              "role": "assistant",
              "content": [
                {"type": "thinking", "thinking": [{"type": "text", "text": "still thinking"}]}
              ]
            }
            """);

    final ChatResult result = mistralConverter.toResult(completion);

    assertThat(result.assistantMessage().content())
        .containsExactly(
            new ReasoningContent("mistral", Map.of("type", "thinking"), "still thinking", null));
  }

  @Test
  void keepsMultiItemThinkingArrayInPayloadInsteadOfCollapsingIt() {
    // A multi-item thinking array (or one whose item carries extra fields) can't be reconstructed
    // byte-identical from the joined text alone, so it must stay in the payload verbatim -- see
    // MistralReasoningContentDialect#isThinkingReconstructible.
    final ChatCompletion completion =
        completionWithFinishReason(
            "stop",
            """
            {
              "role": "assistant",
              "content": [
                {
                  "type": "thinking",
                  "thinking": [
                    {"type": "text", "text": "5 + 7 "},
                    {"type": "text", "text": "is 12."}
                  ],
                  "closed": true
                }
              ]
            }
            """);

    final ChatResult result = mistralConverter.toResult(completion);

    final var reasoning = (ReasoningContent) result.assistantMessage().content().get(0);
    assertThat(reasoning.text()).isEqualTo("5 + 7 is 12.");
    assertThat(reasoning.payload())
        .asInstanceOf(InstanceOfAssertFactories.MAP)
        .containsEntry(
            "thinking",
            List.of(
                Map.of("type", "text", "text", "5 + 7 "),
                Map.of("type", "text", "text", "is 12.")));
  }

  @Test
  void treatsThinkingChunkAsGenericProviderContentWhenDialectDoesNotRecognizeIt() {
    // Unlike mistralConverter, this converter's OpenAiCompletionsContentDialect.none() doesn't
    // recognize Mistral's thinking-chunk vocabulary, so it falls back to the generic
    // unknown-chunk-type handling instead of ReasoningContent.
    final ChatCompletion completion =
        baseCompletion(
            """
            {
              "role": "assistant",
              "content": [
                {"type": "thinking", "thinking": [{"type": "text", "text": "5 + 7 is 12."}]}
              ]
            }
            """);

    final ChatResult result = converter.toResult(completion);

    assertThat(result.assistantMessage().content())
        .singleElement()
        .isInstanceOfSatisfying(
            ProviderContent.class,
            providerContent -> assertThat(providerContent.provider()).isEqualTo("openai"));
  }
}
