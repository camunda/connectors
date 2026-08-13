/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.core.ObjectMappers;
import com.openai.models.chat.completions.ChatCompletion;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatResult;
import io.camunda.connector.agenticai.aiagent.chatmodel.ContentFilteredException;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.message.StopReason;
import io.camunda.connector.agenticai.aiagent.model.message.content.ReasoningContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.util.AssistantMessageMetadata;
import io.camunda.connector.api.error.ConnectorException;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link ChatCompletion} is a response-side vendor SDK model whose generated {@code Builder}
 * requires every field (including the ones exposed as {@code Optional} on the read side) to be set
 * explicitly, which makes it impractical for hand-assembling test fixtures. Deserializing a small
 * JSON fixture via the SDK's own {@link ObjectMappers#jsonMapper()} (mirroring the {@code
 * OpenAiResponsesResponseConverterTest} sibling) is far less brittle, so all fixtures here go
 * through that path instead of the builders.
 */
class OpenAiCompletionsResponseConverterTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final OpenAiCompletionsResponseConverter converter =
      new OpenAiCompletionsResponseConverter(objectMapper);

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
  void throwsConnectorExceptionWhenNoChoicesReturned() {
    final ChatCompletion completion =
        completionFromJson(
            """
            {
              "id": "chatcmpl_123", "object": "chat.completion", "created": 0, "model": "gpt-4o",
              "choices": [],
              "usage": null
            }
            """);

    assertThatThrownBy(() -> converter.toResult(completion, Duration.ofMillis(100)))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ERROR_CODE_FAILED_MODEL_CALL));
  }

  @Test
  void mapsContentToTextContent() {
    final ChatCompletion completion =
        baseCompletion(
            """
            {"role": "assistant", "content": "Hello there"}
            """);

    final ChatResult result = converter.toResult(completion, Duration.ofMillis(100));

    assertThat(result).isInstanceOf(ChatResult.Completed.class);
    assertThat(result.assistantMessage().content())
        .containsExactly(TextContent.textContent("Hello there"));
    assertThat(result.assistantMessage().toolCalls()).isEmpty();
    assertThat(result.assistantMessage().messageId()).isEqualTo("chatcmpl_123");
    assertThat(result.assistantMessage().modelId()).isEqualTo("gpt-4o");
    assertThat(result.assistantMessage().stopReason()).isEqualTo(StopReason.STOP);
  }

  @Test
  void mapsBlankContentToNoTextContent() {
    final ChatCompletion completion =
        baseCompletion(
            """
            {"role": "assistant", "content": "   "}
            """);

    final ChatResult result = converter.toResult(completion, Duration.ofMillis(100));

    assertThat(result.assistantMessage().content()).isEmpty();
  }

  @Test
  void throwsContentFilteredExceptionForRefusal() {
    assertThatThrownBy(() -> converter.toResult(completionWithRefusal(), Duration.ofMillis(100)))
        .isInstanceOfSatisfying(
            ContentFilteredException.class,
            e -> {
              assertThat(e.partialResult()).isNotNull();
              assertThat(e.partialResult().assistantMessage().content())
                  .containsExactly(TextContent.textContent("I can't help with that."));
            });
  }

  private static ChatCompletion completionWithRefusal() {
    return baseCompletion(
        """
        {"role": "assistant", "content": null, "refusal": "I can't help with that."}
        """);
  }

  @Test
  void mapsToolCallsToToolCall() {
    final ChatCompletion completion =
        completionWithFinishReason(
            "tool_calls",
            """
            {
              "role": "assistant",
              "content": null,
              "tool_calls": [
                {
                  "id": "call_1",
                  "type": "function",
                  "function": {"name": "get_weather", "arguments": "{\\"city\\":\\"Berlin\\"}"}
                }
              ]
            }
            """);

    final ChatResult result = converter.toResult(completion, Duration.ofMillis(100));

    assertThat(result.assistantMessage().toolCalls())
        .containsExactly(
            ToolCall.builder()
                .id("call_1")
                .name("get_weather")
                .arguments(Map.of("city", "Berlin"))
                .build());
    assertThat(result.assistantMessage().content()).isEmpty();
    assertThat(result.assistantMessage().stopReason()).isEqualTo(StopReason.TOOL_USE);
  }

  @Test
  void dropsNonStandardReasoningFieldFromMessage() {
    // Chat Completions has no standard reasoning/thinking field on the message; this simulates a
    // nonstandard extension carrying one, verifying it is never surfaced as ReasoningContent --
    // reasoning support is deferred for the Completions family.
    final ChatCompletion completion =
        baseCompletion(
            """
            {
              "role": "assistant",
              "content": "Hello there",
              "reasoning": "Let me think about this...",
              "thinking": "Some internal deliberation"
            }
            """);

    final ChatResult result = converter.toResult(completion, Duration.ofMillis(100));

    assertThat(result.assistantMessage().content())
        .containsExactly(TextContent.textContent("Hello there"));
    assertThat(result.assistantMessage().content()).noneMatch(ReasoningContent.class::isInstance);
  }

  @Test
  void mapsUsageToTokenUsageAndReturnsCompleted() {
    final ChatCompletion completion =
        baseCompletion(
            """
            {"role": "assistant", "content": "Hi"}
            """,
            """
            {
              "prompt_tokens": 100,
              "completion_tokens": 50,
              "total_tokens": 150,
              "prompt_tokens_details": {"cached_tokens": 20},
              "completion_tokens_details": {"reasoning_tokens": 10}
            }
            """);

    final ChatResult result = converter.toResult(completion, Duration.ofMillis(250));

    assertThat(result).isInstanceOf(ChatResult.Completed.class);

    final AgentMetrics metrics = result.metrics();
    assertThat(metrics.modelCalls()).isEqualTo(1);
    assertThat(metrics.toolCalls()).isZero();
    assertThat(metrics.executionTime()).isEqualTo(Duration.ofMillis(250));
    // prompt_tokens (100) includes the 20 cached tokens; inputTokenCount must exclude them so it
    // reflects only new, non-cached input tokens (see AgentMetrics.TokenUsage javadoc).
    assertThat(metrics.tokenUsage().inputTokenCount()).isEqualTo(80);
    assertThat(metrics.tokenUsage().outputTokenCount()).isEqualTo(50);
    assertThat(metrics.tokenUsage().cacheReadTokenCount()).isEqualTo(20);
    assertThat(metrics.tokenUsage().reasoningTokenCount()).isEqualTo(10);
  }

  @Test
  void surfacesReasoningTokensFromUsage() {
    final ChatCompletion completion =
        baseCompletion(
            """
            {"role": "assistant", "content": "Hi"}
            """,
            """
            {
              "prompt_tokens": 100,
              "completion_tokens": 200,
              "total_tokens": 300,
              "completion_tokens_details": {"reasoning_tokens": 128}
            }
            """);

    final ChatResult result = converter.toResult(completion, Duration.ZERO);

    assertThat(result.metrics().tokenUsage().reasoningTokenCount()).isEqualTo(128);
  }

  @Test
  void defaultsReasoningTokensToZeroWhenAbsentFromUsage() {
    final ChatCompletion completion =
        baseCompletion(
            """
            {"role": "assistant", "content": "Hi"}
            """,
            """
            {
              "prompt_tokens": 100,
              "completion_tokens": 50,
              "total_tokens": 150
            }
            """);

    final ChatResult result = converter.toResult(completion, Duration.ofMillis(10));

    assertThat(result.metrics().tokenUsage().reasoningTokenCount()).isZero();
  }

  @Test
  void returnsEmptyTokenUsageWhenUsageAbsent() {
    final ChatCompletion completion =
        baseCompletion(
            """
            {"role": "assistant", "content": "Hi"}
            """);

    final ChatResult result = converter.toResult(completion, Duration.ofMillis(10));

    assertThat(result.metrics().tokenUsage()).isEqualTo(AgentMetrics.TokenUsage.empty());
  }

  @Test
  void mapsStopFinishReasonToStop() {
    final ChatCompletion completion =
        completionWithFinishReason(
            "stop",
            """
            {"role": "assistant", "content": "Hello there"}
            """);

    final ChatResult result = converter.toResult(completion, Duration.ZERO);

    assertThat(result).isInstanceOf(ChatResult.Completed.class);
    assertThat(result.assistantMessage().stopReason()).isEqualTo(StopReason.STOP);
  }

  @Test
  void mapsToolCallsFinishReasonToToolUse() {
    final ChatCompletion completion =
        completionWithFinishReason(
            "tool_calls",
            """
            {
              "role": "assistant",
              "content": null,
              "tool_calls": [
                {
                  "id": "call_1",
                  "type": "function",
                  "function": {"name": "get_weather", "arguments": "{}"}
                }
              ]
            }
            """);

    final ChatResult result = converter.toResult(completion, Duration.ZERO);

    assertThat(result).isInstanceOf(ChatResult.Completed.class);
    assertThat(result.assistantMessage().stopReason()).isEqualTo(StopReason.TOOL_USE);
  }

  @Test
  void throwsContentFilteredExceptionForContentFilterFinishReason() {
    final ChatCompletion completion =
        completionWithFinishReason(
            "content_filter",
            """
            {"role": "assistant", "content": null}
            """);

    assertThatThrownBy(() -> converter.toResult(completion, Duration.ofSeconds(1)))
        .isInstanceOfSatisfying(
            ContentFilteredException.class, e -> assertThat(e.partialResult()).isNotNull());
  }

  @Test
  void mapsLengthFinishReasonToLengthWithoutThrowing() {
    final ChatCompletion completion =
        completionWithFinishReason(
            "length",
            """
            {"role": "assistant", "content": "{\\"partial\\":"}
            """,
            """
            {"prompt_tokens": 10, "completion_tokens": 5, "total_tokens": 15}
            """);

    final ChatResult result = converter.toResult(completion, Duration.ZERO);

    assertThat(result).isInstanceOf(ChatResult.Completed.class);
    assertThat(result.assistantMessage().stopReason()).isEqualTo(StopReason.LENGTH);
  }

  @Test
  void mapsFunctionCallFinishReasonToToolUse() {
    // The legacy singular function_call mechanism (superseded by tool_calls) still indicates the
    // model stopped to invoke a function, so it maps to the same domain StopReason as tool_calls.
    final ChatCompletion completion =
        completionWithFinishReason(
            "function_call",
            """
            {"role": "assistant", "content": null}
            """);

    final ChatResult result = converter.toResult(completion, Duration.ZERO);

    assertThat(result).isInstanceOf(ChatResult.Completed.class);
    assertThat(result.assistantMessage().stopReason()).isEqualTo(StopReason.TOOL_USE);
  }

  @Test
  void stampsTimestampAndRawFinishReasonMetadata() {
    final ChatCompletion completion =
        completionWithFinishReason(
            "tool_calls",
            """
            {
              "role": "assistant",
              "content": null,
              "tool_calls": [
                {
                  "id": "call_1",
                  "type": "function",
                  "function": {"name": "get_weather", "arguments": "{}"}
                }
              ]
            }
            """);

    final ChatResult result = converter.toResult(completion, Duration.ZERO);

    assertThat(result.assistantMessage().metadata())
        .containsKey(AssistantMessageMetadata.TIMESTAMP_KEY)
        .containsEntry("openai", Map.of("stopReason", "tool_calls"));
  }

  @Test
  void mapsUnrecognizedFinishReasonToUnknownStopReason() {
    final ChatCompletion completion =
        completionWithFinishReason(
            "some_future_reason",
            """
            {"role": "assistant", "content": "Hi"}
            """);

    final ChatResult result = converter.toResult(completion, Duration.ZERO);

    assertThat(result).isInstanceOf(ChatResult.Completed.class);
    assertThat(result.assistantMessage().stopReason())
        .isEqualTo(new StopReason.UnknownStopReason("some_future_reason"));
  }
}
