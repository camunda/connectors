/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.anthropic;

import static org.assertj.core.api.Assertions.assertThat;

import com.anthropic.core.JsonValue;
import com.anthropic.core.ObjectMappers;
import com.anthropic.helpers.BetaMessageAccumulator;
import com.anthropic.models.beta.messages.BetaCacheCreation;
import com.anthropic.models.beta.messages.BetaContainer;
import com.anthropic.models.beta.messages.BetaContextManagementResponse;
import com.anthropic.models.beta.messages.BetaDiagnostics;
import com.anthropic.models.beta.messages.BetaDirectCaller;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaMessageDeltaUsage;
import com.anthropic.models.beta.messages.BetaOutputTokensDetails;
import com.anthropic.models.beta.messages.BetaRawContentBlockDeltaEvent;
import com.anthropic.models.beta.messages.BetaRawContentBlockStartEvent;
import com.anthropic.models.beta.messages.BetaRawContentBlockStopEvent;
import com.anthropic.models.beta.messages.BetaRawMessageDeltaEvent;
import com.anthropic.models.beta.messages.BetaRawMessageStartEvent;
import com.anthropic.models.beta.messages.BetaRawMessageStopEvent;
import com.anthropic.models.beta.messages.BetaRawMessageStreamEvent;
import com.anthropic.models.beta.messages.BetaRefusalStopDetails;
import com.anthropic.models.beta.messages.BetaServerToolUsage;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.BetaToolUseBlock;
import com.anthropic.models.beta.messages.BetaUsage;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelResult;
import io.camunda.connector.agenticai.aiagent.model.message.content.ProviderContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ReasoningContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AnthropicMessageResponseConverterTest {

  private static final Duration EXECUTION_TIME = Duration.ofMillis(42);

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final AnthropicMessageResponseConverter converter =
      new AnthropicMessageResponseConverter(objectMapper);

  private static BetaMessage message(String json) {
    try {
      return ObjectMappers.jsonMapper().readValue(json, BetaMessage.class);
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
  void mapsThinkingToReasoningContentWithRawBlockPayloadAndNullText() {
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

    // `text` is intentionally null: the human-readable thinking is carried only once, inside the
    // raw block payload, rather than duplicated into the neutral surface.
    assertThat(assistantMessage.content())
        .containsExactly(
            new ReasoningContent(
                null,
                Map.of(
                    "type",
                    "thinking",
                    "thinking",
                    "Let me think it through",
                    "signature",
                    "sig-123"),
                null),
            TextContent.textContent("the answer"));
  }

  @Test
  void mapsRedactedThinkingToReasoningContentWithRawBlockPayloadAndNullText() {
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
                null, Map.of("type", "redacted_thinking", "data", "encrypted-blob"), null),
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
                "server_tool_use",
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
                "code_execution_tool_result",
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

    assertThat(endTurnResult).isInstanceOf(ChatModelResult.Completed.class);
    assertThat(endTurnResult.assistantMessage().stopReason())
        .isEqualTo(io.camunda.connector.agenticai.aiagent.model.message.StopReason.STOP);

    assertThat(maxTokensResult).isInstanceOf(ChatModelResult.Completed.class);
    assertThat(maxTokensResult.assistantMessage().stopReason())
        .isEqualTo(io.camunda.connector.agenticai.aiagent.model.message.StopReason.LENGTH);
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
    // Drives the *real* vendor SDK BetaMessageAccumulator through the exact event sequence
    // Anthropic streams for a no-argument tool call: a content_block_start for the tool_use
    // block, followed by an *empty* input_json_delta, followed by content_block_stop. The
    // accumulator concatenates the (empty) partial JSON and finalizes the block's input as
    // JsonMissing rather than an empty object -- this is the faithful reproduction of the
    // reported crash, as opposed to the buffered deserialization path exercised above.
    final BetaMessage message = accumulateNoArgumentToolUseMessage();

    final var result = converter.toResult(message, EXECUTION_TIME);

    assertThat(result.assistantMessage().toolCalls())
        .containsExactly(new ToolCall("toolu_1", "now", Map.of()));
  }

  private static BetaMessage accumulateNoArgumentToolUseMessage() {
    final BetaMessageAccumulator acc = BetaMessageAccumulator.create();
    final BetaMessage shell =
        BetaMessage.builder()
            .id("msg-1")
            .container((BetaContainer) null)
            .content(List.of())
            .contextManagement((BetaContextManagementResponse) null)
            .diagnostics((BetaDiagnostics) null)
            .model("test-model")
            .stopDetails((BetaRefusalStopDetails) null)
            .stopReason((BetaStopReason) null)
            .stopSequence((String) null)
            .usage(
                BetaUsage.builder()
                    .inputTokens(1)
                    .outputTokens(0)
                    .cacheCreation((BetaCacheCreation) null)
                    .cacheCreationInputTokens((Long) null)
                    .cacheReadInputTokens((Long) null)
                    .inferenceGeo((String) null)
                    .iterations(List.of())
                    .outputTokensDetails((BetaOutputTokensDetails) null)
                    .serverToolUse((BetaServerToolUsage) null)
                    .serviceTier((BetaUsage.ServiceTier) null)
                    .speed((BetaUsage.Speed) null)
                    .build())
            .build();

    acc.accumulate(
        BetaRawMessageStreamEvent.ofMessageStart(
            BetaRawMessageStartEvent.builder().message(shell).build()));
    acc.accumulate(
        BetaRawMessageStreamEvent.ofContentBlockStart(
            BetaRawContentBlockStartEvent.builder()
                .contentBlock(
                    BetaToolUseBlock.builder()
                        .id("toolu_1")
                        .name("now")
                        .caller(BetaDirectCaller.builder().build())
                        .input(JsonValue.from(Map.of()))
                        .build())
                .index(0)
                .build()));
    acc.accumulate(
        BetaRawMessageStreamEvent.ofContentBlockDelta(
            BetaRawContentBlockDeltaEvent.builder()
                .inputJsonDelta("") // EMPTY delta = no-arg call
                .index(0)
                .build()));
    acc.accumulate(
        BetaRawMessageStreamEvent.ofContentBlockStop(
            BetaRawContentBlockStopEvent.builder().index(0).build()));
    acc.accumulate(
        BetaRawMessageStreamEvent.ofMessageDelta(
            BetaRawMessageDeltaEvent.builder()
                .contextManagement((BetaContextManagementResponse) null)
                .delta(
                    BetaRawMessageDeltaEvent.Delta.builder()
                        .container((BetaContainer) null)
                        .stopReason(BetaStopReason.TOOL_USE)
                        .stopDetails((BetaRefusalStopDetails) null)
                        .stopSequence((String) null)
                        .build())
                .usage(
                    BetaMessageDeltaUsage.builder()
                        .cacheCreationInputTokens((Long) null)
                        .cacheReadInputTokens((Long) null)
                        .inputTokens(1)
                        .iterations(List.of())
                        .outputTokens(1)
                        .outputTokensDetails((BetaOutputTokensDetails) null)
                        .serverToolUse((BetaServerToolUsage) null)
                        .build())
                .build()));
    acc.accumulate(
        BetaRawMessageStreamEvent.ofMessageStop(BetaRawMessageStopEvent.builder().build()));

    return acc.message();
  }
}
