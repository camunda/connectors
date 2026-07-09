/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.anthropic;

import static org.assertj.core.api.Assertions.assertThat;

import com.anthropic.core.ObjectMappers;
import com.anthropic.models.messages.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelResult;
import io.camunda.connector.agenticai.aiagent.model.message.content.ReasoningContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AnthropicMessageResponseConverterTest {

  private static final Duration EXECUTION_TIME = Duration.ofMillis(42);

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final AnthropicMessageResponseConverter converter =
      new AnthropicMessageResponseConverter(objectMapper);

  private static Message message(String json) {
    try {
      return ObjectMappers.jsonMapper().readValue(json, Message.class);
    } catch (Exception e) {
      throw new RuntimeException("Failed to deserialize Message fixture", e);
    }
  }

  @Test
  void mapsTextAndToolUseAndStopReason() {
    final var message =
        message(
            """
            {
              "id": "msg_1",
              "model": "claude-sonnet-4-6",
              "role": "assistant",
              "type": "message",
              "content": [
                {"type": "text", "text": "Hello there"},
                {"type": "tool_use", "id": "toolu_1", "name": "get_weather", "input": {"city": "Berlin"}}
              ],
              "stop_reason": "tool_use",
              "usage": {"input_tokens": 10, "output_tokens": 20}
            }
            """);

    final var result = converter.toResult(message, EXECUTION_TIME);

    assertThat(result).isInstanceOf(ChatModelResult.Completed.class);

    final var assistantMessage = result.assistantMessage();
    assertThat(assistantMessage.content()).containsExactly(TextContent.textContent("Hello there"));
    assertThat(assistantMessage.toolCalls())
        .containsExactly(new ToolCall("toolu_1", "get_weather", Map.of("city", "Berlin")));
    assertThat(assistantMessage.messageId()).isEqualTo("msg_1");
    assertThat(assistantMessage.modelId()).isEqualTo("claude-sonnet-4-6");
    assertThat(assistantMessage.stopReason())
        .isEqualTo(io.camunda.connector.agenticai.aiagent.model.message.StopReason.TOOL_USE);
    assertThat(assistantMessage.metadata()).containsEntry("stopReason", "tool_use");

    final var metrics = result.metrics();
    assertThat(metrics.modelCalls()).isEqualTo(1);
    assertThat(metrics.toolCalls()).isEqualTo(1);
    assertThat(metrics.tokenUsage().inputTokenCount()).isEqualTo(10);
    assertThat(metrics.tokenUsage().outputTokenCount()).isEqualTo(20);
    assertThat(metrics.executionTime()).isEqualTo(EXECUTION_TIME);
  }

  @Test
  void mapsPauseTurnToContinuation() {
    final var message =
        message(
            """
            {
              "id": "msg_2",
              "model": "claude-sonnet-4-6",
              "role": "assistant",
              "type": "message",
              "content": [{"type": "text", "text": "partial answer"}],
              "stop_reason": "pause_turn",
              "usage": {"input_tokens": 5, "output_tokens": 5}
            }
            """);

    final var result = converter.toResult(message, EXECUTION_TIME);

    assertThat(result).isInstanceOf(ChatModelResult.Continuation.class);
    // pause_turn is a continuation signal, not a finished-turn stop reason.
    assertThat(result.assistantMessage().stopReason()).isNull();
    assertThat(result.assistantMessage().metadata()).containsEntry("stopReason", "pause_turn");
  }

  @Test
  void mapsThinkingToReadOnlyReasoningContent() {
    final var message =
        message(
            """
            {
              "id": "msg_3",
              "model": "claude-sonnet-4-6",
              "role": "assistant",
              "type": "message",
              "content": [
                {"type": "thinking", "thinking": "Let me think it through", "signature": "sig-123"},
                {"type": "text", "text": "the answer"}
              ],
              "stop_reason": "end_turn",
              "usage": {"input_tokens": 1, "output_tokens": 1}
            }
            """);

    final var assistantMessage = converter.toResult(message, EXECUTION_TIME).assistantMessage();

    assertThat(assistantMessage.content())
        .containsExactly(
            new ReasoningContent("Let me think it through", "sig-123", null),
            TextContent.textContent("the answer"));
  }

  @Test
  void populatesCacheAndReasoningTokenSubsets() {
    final var message =
        message(
            """
            {
              "id": "msg_4",
              "model": "claude-sonnet-4-6",
              "role": "assistant",
              "type": "message",
              "content": [{"type": "text", "text": "ok"}],
              "stop_reason": "end_turn",
              "usage": {
                "input_tokens": 100,
                "output_tokens": 50,
                "cache_read_input_tokens": 3,
                "cache_creation_input_tokens": 4,
                "output_tokens_details": {"thinking_tokens": 5}
              }
            }
            """);

    final var tokenUsage = converter.toResult(message, EXECUTION_TIME).metrics().tokenUsage();

    assertThat(tokenUsage.inputTokenCount()).isEqualTo(100);
    assertThat(tokenUsage.outputTokenCount()).isEqualTo(50);
    assertThat(tokenUsage.cacheReadTokenCount()).isEqualTo(3);
    assertThat(tokenUsage.cacheCreationTokenCount()).isEqualTo(4);
    assertThat(tokenUsage.reasoningTokenCount()).isEqualTo(5);
  }

  @Test
  void mapsEndTurnToStopAndMaxTokensToLength() {
    final var endTurn =
        message(
            """
            {
              "id": "msg_5",
              "model": "claude-sonnet-4-6",
              "role": "assistant",
              "type": "message",
              "content": [{"type": "text", "text": "done"}],
              "stop_reason": "end_turn",
              "usage": {"input_tokens": 1, "output_tokens": 1}
            }
            """);
    final var maxTokens =
        message(
            """
            {
              "id": "msg_6",
              "model": "claude-sonnet-4-6",
              "role": "assistant",
              "type": "message",
              "content": [{"type": "text", "text": "cut off"}],
              "stop_reason": "max_tokens",
              "usage": {"input_tokens": 1, "output_tokens": 1}
            }
            """);

    final var endTurnResult = converter.toResult(endTurn, EXECUTION_TIME);
    final var maxTokensResult = converter.toResult(maxTokens, EXECUTION_TIME);

    assertThat(endTurnResult).isInstanceOf(ChatModelResult.Completed.class);
    assertThat(endTurnResult.assistantMessage().stopReason())
        .isEqualTo(io.camunda.connector.agenticai.aiagent.model.message.StopReason.STOP);

    assertThat(maxTokensResult).isInstanceOf(ChatModelResult.Completed.class);
    assertThat(maxTokensResult.assistantMessage().stopReason())
        .isEqualTo(io.camunda.connector.agenticai.aiagent.model.message.StopReason.LENGTH);
  }
}
