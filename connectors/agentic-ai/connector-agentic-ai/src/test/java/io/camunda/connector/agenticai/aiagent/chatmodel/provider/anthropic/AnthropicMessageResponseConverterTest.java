/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.anthropic.core.JsonValue;
import com.anthropic.core.ObjectMappers;
import com.anthropic.helpers.MessageAccumulator;
import com.anthropic.models.messages.CacheCreation;
import com.anthropic.models.messages.Container;
import com.anthropic.models.messages.DirectCaller;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageDeltaUsage;
import com.anthropic.models.messages.OutputTokensDetails;
import com.anthropic.models.messages.RawContentBlockDeltaEvent;
import com.anthropic.models.messages.RawContentBlockStartEvent;
import com.anthropic.models.messages.RawContentBlockStopEvent;
import com.anthropic.models.messages.RawMessageDeltaEvent;
import com.anthropic.models.messages.RawMessageStartEvent;
import com.anthropic.models.messages.RawMessageStopEvent;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.RefusalStopDetails;
import com.anthropic.models.messages.ServerToolUsage;
import com.anthropic.models.messages.ToolUseBlock;
import com.anthropic.models.messages.Usage;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatResult;
import io.camunda.connector.agenticai.aiagent.chatmodel.ContentFilteredException;
import io.camunda.connector.agenticai.aiagent.chatmodel.ContextWindowExceededException;
import io.camunda.connector.agenticai.aiagent.model.message.StopReason;
import io.camunda.connector.agenticai.aiagent.model.message.StopReason.UnknownStopReason;
import io.camunda.connector.agenticai.aiagent.model.message.content.ProviderContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ReasoningContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.util.AssistantMessageMetadata;
import java.time.Duration;
import java.util.List;
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

    assertThat(result).isInstanceOf(ChatResult.Completed.class);

    final var assistantMessage = result.assistantMessage();
    assertThat(assistantMessage.content()).containsExactly(TextContent.textContent("Hello there"));
    assertThat(assistantMessage.toolCalls())
        .containsExactly(new ToolCall("toolu_1", "get_weather", Map.of("city", "Berlin")));
    assertThat(assistantMessage.messageId()).isEqualTo("msg_1");
    assertThat(assistantMessage.modelId()).isEqualTo("claude-sonnet-4-6");
    assertThat(assistantMessage.stopReason()).isEqualTo(StopReason.TOOL_USE);
    assertThat(assistantMessage.metadata())
        .containsEntry("anthropic", Map.of("stopReason", "tool_use"))
        .containsKey(AssistantMessageMetadata.TIMESTAMP_KEY);

    final var metrics = result.metrics();
    assertThat(metrics.modelCalls()).isEqualTo(1);
    assertThat(metrics.toolCalls()).isEqualTo(1);
    assertThat(metrics.tokenUsage().inputTokenCount()).isEqualTo(10);
    assertThat(metrics.tokenUsage().outputTokenCount()).isEqualTo(20);
    assertThat(metrics.executionTime()).isEqualTo(EXECUTION_TIME);
  }

  @Test
  void stampsTimestampMetadataEvenWithoutAStopReason() {
    final var message =
        message(
            """
            {
              "id": "msg_no_stop_reason",
              "model": "claude-sonnet-4-6",
              "role": "assistant",
              "type": "message",
              "content": [{"type": "text", "text": "partial answer"}],
              "usage": {"input_tokens": 5, "output_tokens": 5}
            }
            """);

    final var assistantMessage = converter.toResult(message, EXECUTION_TIME).assistantMessage();

    assertThat(assistantMessage.metadata())
        .containsOnlyKeys(AssistantMessageMetadata.TIMESTAMP_KEY);
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

    assertThat(result).isInstanceOf(ChatResult.Continuation.class);
    // pause_turn still surfaces as a stop reason on the assistant message (there's no dedicated
    // domain value for it), even though the result itself is a Continuation, not a Completed.
    assertThat(result.assistantMessage().stopReason())
        .isEqualTo(new UnknownStopReason("pause_turn"));
    assertThat(result.assistantMessage().metadata())
        .containsEntry("anthropic", Map.of("stopReason", "pause_turn"));
  }

  @Test
  void mapsThinkingToReasoningContentWithRawBlockPayload() {
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
            new ReasoningContent(
                "anthropic",
                Map.of("type", "thinking", "signature", "sig-123"),
                "Let me think it through",
                null),
            TextContent.textContent("the answer"));
  }

  @Test
  void mapsThinkingWithEmptyTextToReasoningContentWithoutLiftingIt() {
    // Anthropic can return an empty `thinking` string; nothing to lift, same as redacted thinking.
    final var message =
        message(
            """
            {
              "id": "msg_empty_thinking",
              "model": "claude-sonnet-4-6",
              "role": "assistant",
              "type": "message",
              "content": [
                {"type": "thinking", "thinking": "", "signature": "sig-123"},
                {"type": "text", "text": "the answer"}
              ],
              "stop_reason": "end_turn",
              "usage": {"input_tokens": 1, "output_tokens": 1}
            }
            """);

    final var assistantMessage = converter.toResult(message, EXECUTION_TIME).assistantMessage();

    assertThat(assistantMessage.content())
        .containsExactly(
            new ReasoningContent(
                "anthropic",
                Map.of("type", "thinking", "thinking", "", "signature", "sig-123"),
                null,
                null),
            TextContent.textContent("the answer"));
  }

  @Test
  void mapsThinkingWithWhitespaceOnlyTextToReasoningContentWithoutLiftingIt() {
    // Whitespace-only `thinking` text is treated as absent, same as an empty string.
    final var message =
        message(
            """
            {
              "id": "msg_blank_thinking",
              "model": "claude-sonnet-4-6",
              "role": "assistant",
              "type": "message",
              "content": [
                {"type": "thinking", "thinking": "   ", "signature": "sig-123"},
                {"type": "text", "text": "the answer"}
              ],
              "stop_reason": "end_turn",
              "usage": {"input_tokens": 1, "output_tokens": 1}
            }
            """);

    final var assistantMessage = converter.toResult(message, EXECUTION_TIME).assistantMessage();

    assertThat(assistantMessage.content())
        .containsExactly(
            new ReasoningContent(
                "anthropic",
                Map.of("type", "thinking", "thinking", "   ", "signature", "sig-123"),
                null,
                null),
            TextContent.textContent("the answer"));
  }

  @Test
  void mapsRedactedThinkingToReasoningContentWithRawBlockPayload() {
    final var message =
        message(
            """
            {
              "id": "msg_redacted",
              "model": "claude-sonnet-4-6",
              "role": "assistant",
              "type": "message",
              "content": [
                {"type": "redacted_thinking", "data": "encrypted-blob"},
                {"type": "text", "text": "the answer"}
              ],
              "stop_reason": "end_turn",
              "usage": {"input_tokens": 1, "output_tokens": 1}
            }
            """);

    final var assistantMessage = converter.toResult(message, EXECUTION_TIME).assistantMessage();

    assertThat(assistantMessage.content())
        .containsExactly(
            new ReasoningContent(
                "anthropic",
                Map.of("type", "redacted_thinking", "data", "encrypted-blob"),
                null,
                null),
            TextContent.textContent("the answer"));
  }

  @Test
  void mapsServerToolBlocksToProviderContentPreservingOrder() {
    final var message =
        message(
            """
            {
              "id": "msg_srv",
              "model": "claude-sonnet-4-6",
              "role": "assistant",
              "type": "message",
              "content": [
                {"type": "text", "text": "working"},
                {
                  "type": "server_tool_use",
                  "id": "srvtoolu_01",
                  "name": "code_execution",
                  "input": {"code": "print(1)"}
                },
                {
                  "type": "code_execution_tool_result",
                  "tool_use_id": "srvtoolu_01",
                  "content": {
                    "type": "code_execution_result",
                    "stdout": "1\\n",
                    "stderr": "",
                    "return_code": 0
                  }
                },
                {"type": "text", "text": "done"}
              ],
              "stop_reason": "end_turn",
              "usage": {"input_tokens": 1, "output_tokens": 1}
            }
            """);

    final var assistantMessage = converter.toResult(message, EXECUTION_TIME).assistantMessage();

    assertThat(assistantMessage.content())
        .containsExactly(
            TextContent.textContent("working"),
            new ProviderContent(
                "anthropic",
                Map.of(
                    "id",
                    "srvtoolu_01",
                    "name",
                    "code_execution",
                    "type",
                    "server_tool_use",
                    "input",
                    Map.of("code", "print(1)")),
                null),
            new ProviderContent(
                "anthropic",
                Map.of(
                    "tool_use_id",
                    "srvtoolu_01",
                    "type",
                    "code_execution_tool_result",
                    "content",
                    Map.of(
                        "type",
                        "code_execution_result",
                        "stdout",
                        "1\n",
                        "stderr",
                        "",
                        "return_code",
                        0L)),
                null),
            TextContent.textContent("done"));
    assertThat(assistantMessage.toolCalls()).isEmpty();
  }

  @Test
  void mapsClientToolUseToToolCallsEvenAlongsideServerToolUseBlocks() {
    // Guards the if/else ordering in the block loop: a client tool_use block must still be routed
    // to toolCalls (and NOT captured as ProviderContent) even when a server_tool_use block --
    // handled by the same catch-all branch as other non-core blocks -- is also present.
    final var message =
        message(
            """
            {
              "id": "msg_mixed",
              "model": "claude-sonnet-4-6",
              "role": "assistant",
              "type": "message",
              "content": [
                {
                  "type": "server_tool_use",
                  "id": "srvtoolu_01",
                  "name": "code_execution",
                  "input": {"code": "print(1)"}
                },
                {"type": "tool_use", "id": "toolu_1", "name": "get_weather", "input": {"city": "Berlin"}}
              ],
              "stop_reason": "tool_use",
              "usage": {"input_tokens": 1, "output_tokens": 1}
            }
            """);

    final var assistantMessage = converter.toResult(message, EXECUTION_TIME).assistantMessage();

    assertThat(assistantMessage.toolCalls())
        .containsExactly(new ToolCall("toolu_1", "get_weather", Map.of("city", "Berlin")));
    assertThat(assistantMessage.content()).hasSize(1).first().isInstanceOf(ProviderContent.class);
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

    assertThat(endTurnResult).isInstanceOf(ChatResult.Completed.class);
    assertThat(endTurnResult.assistantMessage().stopReason()).isEqualTo(StopReason.STOP);

    assertThat(maxTokensResult).isInstanceOf(ChatResult.Completed.class);
    assertThat(maxTokensResult.assistantMessage().stopReason()).isEqualTo(StopReason.LENGTH);
  }

  @Test
  void throwsContentFilteredExceptionForRefusal() {
    final var message =
        message(
            """
            {
              "id": "msg_refusal",
              "model": "claude-sonnet-4-6",
              "role": "assistant",
              "type": "message",
              "content": [{"type": "text", "text": "I can't help with that"}],
              "stop_reason": "refusal",
              "usage": {"input_tokens": 1, "output_tokens": 1}
            }
            """);

    assertThatThrownBy(() -> converter.toResult(message, EXECUTION_TIME))
        .isInstanceOfSatisfying(
            ContentFilteredException.class,
            e -> {
              final var partialResult = e.partialResult();
              assertThat(partialResult).isNotNull();
              assertThat(partialResult.assistantMessage().metadata())
                  .containsEntry("anthropic", Map.of("stopReason", "refusal"));
            });
  }

  @Test
  void throwsContextWindowExceededExceptionForModelContextWindowExceeded() {
    final var message =
        message(
            """
            {
              "id": "msg_context_window_exceeded",
              "model": "claude-sonnet-4-6",
              "role": "assistant",
              "type": "message",
              "content": [{"type": "text", "text": "partial answer"}],
              "stop_reason": "model_context_window_exceeded",
              "usage": {"input_tokens": 1, "output_tokens": 1}
            }
            """);

    assertThatThrownBy(() -> converter.toResult(message, EXECUTION_TIME))
        .isInstanceOfSatisfying(
            ContextWindowExceededException.class,
            e -> {
              final var partialResult = e.partialResult();
              assertThat(partialResult).isNotNull();
              assertThat(partialResult.assistantMessage().content())
                  .containsExactly(TextContent.textContent("partial answer"));
              assertThat(partialResult.assistantMessage().metadata())
                  .containsEntry(
                      "anthropic", Map.of("stopReason", "model_context_window_exceeded"));
            });
  }

  @Test
  void mapsUnrecognisedStopReasonToUnknownStopReasonCarryingTheRawValue() {
    final var message =
        message(
            """
            {
              "id": "msg_8",
              "model": "claude-sonnet-4-6",
              "role": "assistant",
              "type": "message",
              "content": [{"type": "text", "text": "??"}],
              "stop_reason": "some_new_vendor_stop_reason",
              "usage": {"input_tokens": 1, "output_tokens": 1}
            }
            """);

    final var result = converter.toResult(message, EXECUTION_TIME);

    assertThat(result).isInstanceOf(ChatResult.Completed.class);
    assertThat(result.assistantMessage().stopReason())
        .isEqualTo(new UnknownStopReason("some_new_vendor_stop_reason"));
    assertThat(result.assistantMessage().metadata())
        .containsEntry("anthropic", Map.of("stopReason", "some_new_vendor_stop_reason"));
  }

  @Test
  void mapsNoArgumentToolUseWithMissingInputToEmptyArguments() {
    // No "input" key at all -- the vendor SDK deserializes this the same way it finalizes a
    // no-argument tool call streamed via an empty input_json_delta: as JsonMissing, not `{}`.
    final var message =
        message(
            """
            {
              "id": "msg_7",
              "model": "claude-sonnet-4-6",
              "role": "assistant",
              "type": "message",
              "content": [
                {"type": "tool_use", "id": "toolu_1", "name": "now"}
              ],
              "stop_reason": "tool_use",
              "usage": {"input_tokens": 1, "output_tokens": 1}
            }
            """);

    final var result = converter.toResult(message, EXECUTION_TIME);

    assertThat(result.assistantMessage().toolCalls())
        .containsExactly(new ToolCall("toolu_1", "now", Map.of()));
  }

  @Test
  void mapsNoArgumentToolUseFromEmptyInputJsonDeltaStream() {
    // Drives the real vendor SDK MessageAccumulator through the exact streamed event sequence
    // that finalizes a no-argument tool call's input as JsonMissing (reproducing the reported
    // crash), rather than exercising the buffered deserialization path above.
    final Message message = accumulateNoArgumentToolUseMessage();

    final var result = converter.toResult(message, EXECUTION_TIME);

    assertThat(result.assistantMessage().toolCalls())
        .containsExactly(new ToolCall("toolu_1", "now", Map.of()));
  }

  private static Message accumulateNoArgumentToolUseMessage() {
    final MessageAccumulator acc = MessageAccumulator.create();
    final Message shell =
        Message.builder()
            .id("msg-1")
            .container((Container) null)
            .content(List.of())
            .model("test-model")
            .stopDetails((RefusalStopDetails) null)
            .stopReason((com.anthropic.models.messages.StopReason) null)
            .stopSequence((String) null)
            .usage(
                Usage.builder()
                    .inputTokens(1)
                    .outputTokens(0)
                    .cacheCreation((CacheCreation) null)
                    .cacheCreationInputTokens((Long) null)
                    .cacheReadInputTokens((Long) null)
                    .inferenceGeo((String) null)
                    .outputTokensDetails((OutputTokensDetails) null)
                    .serverToolUse((ServerToolUsage) null)
                    .serviceTier((Usage.ServiceTier) null)
                    .build())
            .build();

    acc.accumulate(
        RawMessageStreamEvent.ofMessageStart(
            RawMessageStartEvent.builder().message(shell).build()));
    acc.accumulate(
        RawMessageStreamEvent.ofContentBlockStart(
            RawContentBlockStartEvent.builder()
                .contentBlock(
                    ToolUseBlock.builder()
                        .id("toolu_1")
                        .name("now")
                        .caller(DirectCaller.builder().build())
                        .input(JsonValue.from(Map.of()))
                        .build())
                .index(0)
                .build()));
    acc.accumulate(
        RawMessageStreamEvent.ofContentBlockDelta(
            RawContentBlockDeltaEvent.builder()
                .inputJsonDelta("") // EMPTY delta = no-arg call
                .index(0)
                .build()));
    acc.accumulate(
        RawMessageStreamEvent.ofContentBlockStop(
            RawContentBlockStopEvent.builder().index(0).build()));
    acc.accumulate(
        RawMessageStreamEvent.ofMessageDelta(
            RawMessageDeltaEvent.builder()
                .delta(
                    RawMessageDeltaEvent.Delta.builder()
                        .container((Container) null)
                        .stopReason(com.anthropic.models.messages.StopReason.TOOL_USE)
                        .stopDetails((RefusalStopDetails) null)
                        .stopSequence((String) null)
                        .build())
                .usage(
                    MessageDeltaUsage.builder()
                        .cacheCreationInputTokens((Long) null)
                        .cacheReadInputTokens((Long) null)
                        .inputTokens(1)
                        .outputTokens(1)
                        .outputTokensDetails((OutputTokensDetails) null)
                        .serverToolUse((ServerToolUsage) null)
                        .build())
                .build()));
    acc.accumulate(RawMessageStreamEvent.ofMessageStop(RawMessageStopEvent.builder().build()));

    return acc.message();
  }
}
