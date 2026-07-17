/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.openai.family.responses;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_RESPONSE_TRUNCATED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.core.ObjectMappers;
import com.openai.models.responses.Response;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelResult;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.message.content.ProviderContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ReasoningContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.api.error.ConnectorException;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link Response} and its output items are response-side vendor SDK models: their generated {@code
 * Builder}s require every field (including the ones exposed as {@code Optional} on the read side)
 * to be set explicitly, which makes them impractical for hand-assembling test fixtures.
 * Deserializing a small JSON fixture via the SDK's own {@link ObjectMappers#jsonMapper()} (the
 * approach the SDK itself uses to build these objects from the wire) is far less brittle, so all
 * fixtures here go through that path instead of the builders.
 */
class OpenAiResponsesResponseConverterTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final OpenAiResponsesResponseConverter converter =
      new OpenAiResponsesResponseConverter(objectMapper);

  private static Response responseFromJson(String json) {
    try {
      return ObjectMappers.jsonMapper().readValue(json, Response.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to parse test fixture JSON", e);
    }
  }

  private static Response baseResponse(String outputJson) {
    return baseResponse(outputJson, "null");
  }

  private static Response baseResponse(String outputJson, String usageJson) {
    return responseFromJson(
        """
        {
          "id": "resp_123",
          "object": "response",
          "created_at": 0,
          "model": "gpt-5",
          "output": %s,
          "parallel_tool_calls": true,
          "tool_choice": "auto",
          "tools": [],
          "usage": %s
        }
        """
            .formatted(outputJson, usageJson));
  }

  @Test
  void mapsOutputTextItemToTextContent() {
    final Response response =
        baseResponse(
            """
            [
              {
                "type": "message",
                "id": "msg_1",
                "role": "assistant",
                "status": "completed",
                "content": [
                  {"type": "output_text", "text": "Hello there", "annotations": []}
                ]
              }
            ]
            """);

    final ChatModelResult result = converter.toResult(response, Duration.ofMillis(100));

    assertThat(result).isInstanceOf(ChatModelResult.Completed.class);
    assertThat(result.assistantMessage().content())
        .containsExactly(TextContent.textContent("Hello there"));
    assertThat(result.assistantMessage().toolCalls()).isEmpty();
    assertThat(result.assistantMessage().messageId()).isEqualTo("resp_123");
    assertThat(result.assistantMessage().modelId()).isEqualTo("gpt-5");
  }

  @Test
  void mapsRefusalContentToTextContent() {
    final Response response =
        baseResponse(
            """
            [
              {
                "type": "message",
                "id": "msg_1",
                "role": "assistant",
                "status": "completed",
                "content": [
                  {"type": "refusal", "refusal": "I can't help with that."}
                ]
              }
            ]
            """);

    final ChatModelResult result = converter.toResult(response, Duration.ofMillis(100));

    assertThat(result.assistantMessage().content())
        .containsExactly(TextContent.textContent("I can't help with that."));
    assertThat(result.assistantMessage().toolCalls()).isEmpty();
  }

  @Test
  void mapsFunctionCallItemToToolCall() {
    final Response response =
        baseResponse(
            """
            [
              {
                "type": "function_call",
                "id": "fc_1",
                "call_id": "call_1",
                "name": "get_weather",
                "arguments": "{\\"city\\":\\"Berlin\\"}",
                "status": "completed"
              }
            ]
            """);

    final ChatModelResult result = converter.toResult(response, Duration.ofMillis(100));

    assertThat(result.assistantMessage().toolCalls())
        .containsExactly(
            ToolCall.builder()
                .id("call_1")
                .name("get_weather")
                .arguments(Map.of("city", "Berlin"))
                .build());
    assertThat(result.assistantMessage().content()).isEmpty();
  }

  @Test
  void mapsReasoningItemToReasoningContentWithProviderPayload() {
    final Response response =
        baseResponse(
            """
            [
              {
                "type": "reasoning",
                "id": "rs_1",
                "summary": [{"type": "summary_text", "text": "Thinking about it"}],
                "encrypted_content": "encrypted-blob"
              }
            ]
            """);

    final ChatModelResult result = converter.toResult(response, Duration.ofMillis(100));

    assertThat(result.assistantMessage().content()).hasSize(1);
    final ReasoningContent reasoningContent =
        (ReasoningContent) result.assistantMessage().content().get(0);
    assertThat(reasoningContent.text()).isEqualTo("Thinking about it");

    @SuppressWarnings("unchecked")
    final Map<String, Object> payload = (Map<String, Object>) reasoningContent.providerPayload();
    assertThat(payload).containsEntry("id", "rs_1");
    assertThat(payload).containsEntry("type", "reasoning");
    assertThat(payload).containsEntry("encrypted_content", "encrypted-blob");
  }

  @Test
  void mapsReasoningItemWithoutSummaryToNullText() {
    final Response response =
        baseResponse(
            """
            [
              {"type": "reasoning", "id": "rs_2", "summary": []}
            ]
            """);

    final ChatModelResult result = converter.toResult(response, Duration.ofMillis(100));

    final ReasoningContent reasoningContent =
        (ReasoningContent) result.assistantMessage().content().get(0);
    assertThat(reasoningContent.text()).isNull();
  }

  @Test
  @SuppressWarnings("unchecked")
  void mapsWebSearchAndCodeInterpreterItemsToProviderContent() {
    final Response response =
        baseResponse(
            """
            [
              {
                "type": "web_search_call",
                "id": "ws_1",
                "status": "completed",
                "action": {"type": "search", "query": "weather berlin"}
              },
              {
                "type": "code_interpreter_call",
                "id": "ci_1",
                "status": "completed",
                "container_id": "container_1"
              }
            ]
            """);

    final ChatModelResult result = converter.toResult(response, Duration.ofMillis(100));

    final var content = result.assistantMessage().content();
    assertThat(content).hasSize(2);

    final ProviderContent webSearchContent = (ProviderContent) content.get(0);
    assertThat(webSearchContent.provider()).isEqualTo("openai");
    assertThat(webSearchContent.blockType()).isEqualTo("web_search_call");
    assertThat((Map<String, Object>) webSearchContent.payload()).containsEntry("id", "ws_1");

    final ProviderContent codeInterpreterContent = (ProviderContent) content.get(1);
    assertThat(codeInterpreterContent.provider()).isEqualTo("openai");
    assertThat(codeInterpreterContent.blockType()).isEqualTo("code_interpreter_call");
    assertThat((Map<String, Object>) codeInterpreterContent.payload()).containsEntry("id", "ci_1");
  }

  @Test
  @SuppressWarnings("unchecked")
  void mapsUnhandledRecognizedOutputItemToProviderContentFallback() {
    // file_search_call is a recognized ResponseOutputItem variant that the converter does not
    // explicitly branch on, exercising the faithfulness fallback for any output item kind not
    // handled by name (whether genuinely unknown to this SDK version, or simply not yet mapped).
    final Response response =
        baseResponse(
            """
            [
              {"type": "file_search_call", "id": "fs_1", "status": "completed", "queries": []}
            ]
            """);

    final ChatModelResult result = converter.toResult(response, Duration.ofMillis(100));

    final var content = result.assistantMessage().content();
    assertThat(content).hasSize(1);

    final ProviderContent providerContent = (ProviderContent) content.get(0);
    assertThat(providerContent.provider()).isEqualTo("openai");
    assertThat(providerContent.blockType()).isEqualTo("file_search_call");
    assertThat((Map<String, Object>) providerContent.payload()).isNotEmpty();
    assertThat(result.assistantMessage().toolCalls()).isEmpty();
  }

  @Test
  void mapsStringModelIdToModelId() {
    final Response response =
        responseFromJson(
            """
            {
              "id": "resp_456",
              "object": "response",
              "created_at": 0,
              "model": "my-custom-fine-tuned-model",
              "output": [],
              "parallel_tool_calls": true,
              "tool_choice": "auto",
              "tools": [],
              "usage": null
            }
            """);

    final ChatModelResult result = converter.toResult(response, Duration.ofMillis(10));

    assertThat(result.assistantMessage().modelId()).isEqualTo("my-custom-fine-tuned-model");
  }

  @Test
  void mapsUsageToTokenUsageAndReturnsCompleted() {
    final Response response =
        baseResponse(
            "[]",
            """
            {
              "input_tokens": 100,
              "output_tokens": 50,
              "total_tokens": 150,
              "input_tokens_details": {"cached_tokens": 20},
              "output_tokens_details": {"reasoning_tokens": 10}
            }
            """);

    final ChatModelResult result = converter.toResult(response, Duration.ofMillis(250));

    assertThat(result).isInstanceOf(ChatModelResult.Completed.class);

    final AgentMetrics metrics = result.metrics();
    assertThat(metrics.modelCalls()).isEqualTo(1);
    assertThat(metrics.toolCalls()).isZero();
    assertThat(metrics.executionTime()).isEqualTo(Duration.ofMillis(250));
    assertThat(metrics.tokenUsage().inputTokenCount()).isEqualTo(100);
    assertThat(metrics.tokenUsage().outputTokenCount()).isEqualTo(50);
    assertThat(metrics.tokenUsage().cacheReadTokenCount()).isEqualTo(20);
    assertThat(metrics.tokenUsage().reasoningTokenCount()).isEqualTo(10);
  }

  @Test
  void returnsEmptyTokenUsageWhenUsageAbsent() {
    final Response response = baseResponse("[]");

    final ChatModelResult result = converter.toResult(response, Duration.ofMillis(10));

    assertThat(result.metrics().tokenUsage()).isEqualTo(AgentMetrics.TokenUsage.empty());
  }

  @Test
  void raisesTruncationErrorWhenResponseIsIncompleteDueToMaxOutputTokens() {
    final Response response =
        responseFromJson(
            """
            {
              "id": "resp_1", "object": "response", "created_at": 0, "model": "gpt-5",
              "status": "incomplete",
              "incomplete_details": {"reason": "max_output_tokens"},
              "output": [], "parallel_tool_calls": true, "tool_choice": "auto", "tools": []
            }
            """);

    assertThatThrownBy(() -> converter.toResult(response, Duration.ZERO))
        .isInstanceOf(ConnectorException.class)
        .hasFieldOrPropertyWithValue("errorCode", ERROR_CODE_RESPONSE_TRUNCATED)
        .hasMessageContaining("truncated");
  }
}
