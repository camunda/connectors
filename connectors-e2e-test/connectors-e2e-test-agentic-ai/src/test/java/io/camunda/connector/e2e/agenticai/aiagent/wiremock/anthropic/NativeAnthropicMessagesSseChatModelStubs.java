/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.e2e.agenticai.aiagent.wiremock.anthropic;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.camunda.connector.e2e.agenticai.aiagent.wiremock.anthropic.AnthropicMessagesChatModelStubs.MESSAGES_PATH;

import com.anthropic.core.JsonValue;
import com.anthropic.core.ObjectMappers;
import com.anthropic.models.beta.messages.BetaCacheCreation;
import com.anthropic.models.beta.messages.BetaCodeExecutionResultBlock;
import com.anthropic.models.beta.messages.BetaCodeExecutionToolResultBlock;
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
import com.anthropic.models.beta.messages.BetaRedactedThinkingBlock;
import com.anthropic.models.beta.messages.BetaRefusalStopDetails;
import com.anthropic.models.beta.messages.BetaServerToolUsage;
import com.anthropic.models.beta.messages.BetaServerToolUseBlock;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.BetaTextBlock;
import com.anthropic.models.beta.messages.BetaThinkingBlock;
import com.anthropic.models.beta.messages.BetaThinkingDelta;
import com.anthropic.models.beta.messages.BetaToolUseBlock;
import com.anthropic.models.beta.messages.BetaUsage;
import com.anthropic.models.beta.messages.BetaUsage.ServiceTier;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.ScenarioMappingBuilder;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.ToolCallStub;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.TurnStub;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stubs the Anthropic Messages endpoint's <strong>streaming</strong> response ({@code POST
 * /v1/messages}, always requested with {@code Accept: text/event-stream} and {@code "stream": true}
 * by the native provider) with real Server-Sent-Events framing.
 *
 * <p>{@code AnthropicChatModelApi} (the native, own-LLM-layer Anthropic provider) always drives
 * {@code client.beta().messages().createStreaming(params)} and feeds the raw event stream to the
 * vendor SDK's {@code BetaMessageAccumulator}, which requires a {@code message_start} &rarr; ...
 * &rarr; {@code message_stop} event sequence and throws ({@code IllegalStateException:
 * 'message_stop' event not yet received.}) if handed anything else - in particular the single
 * buffered JSON body that {@link AnthropicMessagesChatModelStubs} (shared with the
 * langchain4j-bridge v1 fixture, whose client issues a plain non-streaming POST) returns.
 *
 * <p>Each event is built using the vendor SDK's own {@code BetaRawMessageStreamEvent} member types
 * (rather than hand-rolled JSON) and serialized with the SDK's own {@link
 * ObjectMappers#jsonMapper()}, so the bytes are guaranteed to parse exactly the way {@code
 * BetaMessageAccumulator} expects them to. The per-turn data (assistant text, tool_use calls,
 * input/output token usage, stop reason) mirrors {@link AnthropicMessagesChatModelStubs.Turn}
 * exactly, just framed as SSE instead of one buffered JSON object:
 *
 * <ol>
 *   <li>{@code message_start} - a {@link BetaMessage} shell (id/type/role=assistant/model, empty
 *       content array, {@code usage.input_tokens} set, {@code stop_reason} explicitly {@code
 *       null}).
 *   <li>Per content block (text first, then each tool call, matching the buffered stub's ordering):
 *       {@code content_block_start} (a {@link BetaTextBlock} with empty {@code text}, or a {@link
 *       BetaToolUseBlock} with empty {@code input}) + one {@code content_block_delta} ({@code
 *       text_delta} carrying the full text, or {@code input_json_delta} carrying the full arguments
 *       JSON in one chunk) + {@code content_block_stop}.
 *   <li>{@code message_delta} - {@code stop_reason} ({@code tool_use} if there were tool calls,
 *       else {@code end_turn}) plus the final {@code usage.output_tokens}.
 *   <li>{@code message_stop}.
 * </ol>
 *
 * <p>{@link #stubServerToolUseConversation(ServerToolUseTurnStub, TurnStub...)} additionally stubs
 * a Skills/code-execution turn - a {@code server_tool_use} block plus its {@code
 * code_execution_tool_result} block, framed the same way - closing the e2e coverage gap that let an
 * earlier native-path bug in that block-mapping ship undetected (see {@link
 * ServerToolUseTurnStub}).
 */
final class NativeAnthropicMessagesSseChatModelStubs {

  private static final String SCENARIO_NAME = "llm-conversation-sse";
  private static final JsonMapper JSON_MAPPER = ObjectMappers.jsonMapper();
  private static final AtomicInteger TURN_COUNTER = new AtomicInteger(0);

  private NativeAnthropicMessagesSseChatModelStubs() {}

  /** Wires the scenario chain returning each turn's SSE response in order. */
  static void stubConversation(TurnStub... turns) {
    if (turns.length == 0) {
      throw new IllegalArgumentException("At least one conversation turn is required");
    }
    stubScenario(
        Arrays.stream(turns).map(NativeAnthropicMessagesSseChatModelStubs::sseBody).toList());
  }

  /**
   * Wires a scenario chain whose first turn is a {@link ServerToolUseTurnStub} - assistant text, an
   * Anthropic {@code server_tool_use} (code_execution) block, the corresponding {@code
   * code_execution_tool_result} block, and trailing text, exactly the shape a real Skills/
   * code-execution turn returns (see {@code AnthropicMessageResponseConverterTest
   * #mapsServerToolBlocksToProviderContentPreservingOrder}) - followed by any number of ordinary
   * {@link TurnStub} turns.
   *
   * <p>Deliberately NOT expressed as a {@link TurnStub} case: {@code TurnStub} is the
   * provider-agnostic SPI shared by every provider's fixture (OpenAI, Azure OpenAI, Bedrock,
   * Anthropic), each with its own exhaustive switch over it. Server-tool blocks are an
   * Anthropic-native-only wire concept with no cross-provider equivalent, so adding a case here
   * would force an unrelated edit onto every other provider's stub for a shape they can never
   * produce. This method - and {@link ServerToolUseTurnStub} - live only on this native-Anthropic
   * SSE stub instead.
   */
  static void stubServerToolUseConversation(
      ServerToolUseTurnStub serverToolUseTurn, TurnStub... followUpTurns) {
    final List<String> bodies = new ArrayList<>();
    bodies.add(serverToolUseSseBody(serverToolUseTurn));
    for (final TurnStub turn : followUpTurns) {
      bodies.add(sseBody(turn));
    }
    stubScenario(bodies);
  }

  /**
   * A turn whose response leads with a real, streamed {@code thinking} block (content_block_start
   * &rarr; {@code thinking_delta} &rarr; {@code signature_delta} &rarr; content_block_stop)
   * followed by one or more client {@code tool_use} blocks - the shape a reasoning-enabled model
   * returns when it thinks before calling a tool. Always ends the turn with {@code stop_reason:
   * tool_use} (there is always at least one tool call), unlike {@link #sseBody(TurnStub)} which
   * derives the stop reason from whether tool calls are present.
   *
   * <p>Exists so e2e coverage can prove the Task 4 round-trip end to end through the REAL {@code
   * BetaMessageAccumulator}: the resulting {@code ReasoningContent}'s raw {@code providerPayload}
   * (captured by {@code AnthropicMessageResponseConverter}) must be replayed byte-identical - same
   * {@code thinking}/{@code signature} values, positioned before the tool-call block, exactly as
   * the domain content ordering preserves it - on the follow-up model call once the tool result is
   * available (see {@code AnthropicContentConverter}/{@code AnthropicMessageRequestConverter
   * #assistantParam}).
   */
  record ThinkingTurnStub(
      String thinking,
      String signature,
      List<ToolCallStub> toolCalls,
      int inputTokens,
      int outputTokens) {}

  /**
   * Wires a scenario chain whose first turn is a {@link ThinkingTurnStub} (signed thinking block
   * plus tool_use), followed by any number of ordinary {@link TurnStub} turns - mirrors {@link
   * #stubServerToolUseConversation(ServerToolUseTurnStub, TurnStub...)}, just for a
   * client-tool-call turn instead of a server-tool turn.
   */
  static void stubThinkingConversation(ThinkingTurnStub thinkingTurn, TurnStub... followUpTurns) {
    final List<String> bodies = new ArrayList<>();
    bodies.add(thinkingSseBody(thinkingTurn));
    for (final TurnStub turn : followUpTurns) {
      bodies.add(sseBody(turn));
    }
    stubScenario(bodies);
  }

  private static String thinkingSseBody(ThinkingTurnStub turn) {
    final int id = TURN_COUNTER.getAndIncrement();
    final StringBuilder body = new StringBuilder();

    writeEvent(body, "message_start", messageStartEvent(id, turn.inputTokens()));

    int index = 0;
    writeThinkingBlock(body, index++, turn.thinking(), turn.signature());
    for (final ToolCallStub toolCall : turn.toolCalls()) {
      writeToolUseBlock(body, index++, toolCall);
    }

    // Always tool_use: a ThinkingTurnStub always carries at least one client tool call.
    writeEvent(
        body, "message_delta", messageDeltaEvent(true, turn.inputTokens(), turn.outputTokens()));
    writeEvent(body, "message_stop", BetaRawMessageStopEvent.builder().build());

    return body.toString();
  }

  /**
   * Frames a {@code thinking} block the same way real Anthropic streams it: an empty shell at
   * {@code content_block_start}, the thinking text streamed via a {@code thinking_delta}, then the
   * cryptographic signature streamed via a {@code signature_delta} - both accumulated by the vendor
   * SDK's {@code BetaMessageAccumulator} onto the same block before {@code content_block_stop}
   * finalizes it, exactly like {@link #writeToolUseBlock}'s {@code input_json_delta} accumulation.
   */
  private static void writeThinkingBlock(
      StringBuilder body, int index, String thinking, String signature) {
    writeEvent(
        body,
        "content_block_start",
        BetaRawContentBlockStartEvent.builder()
            .contentBlock(BetaThinkingBlock.builder().thinking("").signature("").build())
            .index(index)
            .build());
    writeEvent(
        body,
        "content_block_delta",
        BetaRawContentBlockDeltaEvent.builder()
            .delta(BetaThinkingDelta.builder().thinking(thinking).estimatedTokens(0L).build())
            .index(index)
            .build());
    writeEvent(
        body,
        "content_block_delta",
        BetaRawContentBlockDeltaEvent.builder().signatureDelta(signature).index(index).build());
    writeEvent(
        body, "content_block_stop", BetaRawContentBlockStopEvent.builder().index(index).build());
  }

  /**
   * A turn whose response leads with a real {@code redacted_thinking} block - fully formed at
   * {@code content_block_start} with its opaque {@code data} already populated, unlike a plain
   * {@code thinking} block, since real Anthropic streams a redacted block whole with no {@code
   * thinking}/{@code signature} deltas - followed by one or more client {@code tool_use} blocks.
   * Always ends the turn with {@code stop_reason: tool_use} (there is always at least one tool
   * call), mirroring {@link ThinkingTurnStub}.
   *
   * <p>Exists so e2e coverage can prove the redacted-thinking round-trip end to end through the
   * REAL {@code BetaMessageAccumulator}: the resulting {@code ReasoningContent}'s raw {@code
   * providerPayload} (captured by {@code AnthropicMessageResponseConverter}'s {@code
   * block.isRedactedThinking()} branch) must be replayed byte-identical - same {@code data} value,
   * positioned before the tool-call block - on the follow-up model call once the tool result is
   * available (see {@code AnthropicContentConverter}).
   */
  record RedactedThinkingTurnStub(
      String data, List<ToolCallStub> toolCalls, int inputTokens, int outputTokens) {}

  /**
   * Wires a scenario chain whose first turn is a {@link RedactedThinkingTurnStub} (redacted
   * thinking block plus tool_use), followed by any number of ordinary {@link TurnStub} turns -
   * mirrors {@link #stubThinkingConversation(ThinkingTurnStub, TurnStub...)}, just for a redacted
   * thinking block instead of a signed one.
   */
  static void stubRedactedThinkingConversation(
      RedactedThinkingTurnStub redactedTurn, TurnStub... followUpTurns) {
    final List<String> bodies = new ArrayList<>();
    bodies.add(redactedThinkingSseBody(redactedTurn));
    for (final TurnStub turn : followUpTurns) {
      bodies.add(sseBody(turn));
    }
    stubScenario(bodies);
  }

  private static String redactedThinkingSseBody(RedactedThinkingTurnStub turn) {
    final int id = TURN_COUNTER.getAndIncrement();
    final StringBuilder body = new StringBuilder();

    writeEvent(body, "message_start", messageStartEvent(id, turn.inputTokens()));

    int index = 0;
    writeRedactedThinkingBlock(body, index++, turn.data());
    for (final ToolCallStub toolCall : turn.toolCalls()) {
      writeToolUseBlock(body, index++, toolCall);
    }

    // Always tool_use: a RedactedThinkingTurnStub always carries at least one client tool call.
    writeEvent(
        body, "message_delta", messageDeltaEvent(true, turn.inputTokens(), turn.outputTokens()));
    writeEvent(body, "message_stop", BetaRawMessageStopEvent.builder().build());

    return body.toString();
  }

  /**
   * Frames a {@code redacted_thinking} block the way real Anthropic streams it: fully formed
   * already at {@code content_block_start} (opaque {@code data} populated) with no deltas at all -
   * unlike {@link #writeThinkingBlock}'s {@code thinking_delta}/{@code signature_delta}
   * accumulation - since {@code tracksToolInput()} does not cover this block type, the same reason
   * {@link #writeCodeExecutionToolResultBlock} needs no delta either.
   */
  private static void writeRedactedThinkingBlock(StringBuilder body, int index, String data) {
    writeEvent(
        body,
        "content_block_start",
        BetaRawContentBlockStartEvent.builder()
            .contentBlock(BetaRedactedThinkingBlock.builder().data(data).build())
            .index(index)
            .build());
    writeEvent(
        body, "content_block_stop", BetaRawContentBlockStopEvent.builder().index(index).build());
  }

  /** Shared scenario-chaining plumbing: returns each pre-rendered SSE body in order. */
  private static void stubScenario(List<String> bodies) {
    for (int i = 0; i < bodies.size(); i++) {
      final String fromState = i == 0 ? Scenario.STARTED : stateName(i);

      ScenarioMappingBuilder mapping =
          post(urlPathEqualTo(MESSAGES_PATH))
              .inScenario(SCENARIO_NAME)
              .whenScenarioStateIs(fromState)
              .willReturn(sseResponse(bodies.get(i)));

      if (i < bodies.size() - 1) {
        mapping = mapping.willSetStateTo(stateName(i + 1));
      }

      stubFor(mapping);
    }
  }

  private static String stateName(int index) {
    return "turn-" + index;
  }

  private static ResponseDefinitionBuilder sseResponse(String body) {
    return aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "text/event-stream")
        .withBody(body);
  }

  private static String sseBody(TurnStub turn) {
    final int id = TURN_COUNTER.getAndIncrement();
    final String text = turnText(turn);
    final List<ToolCallStub> toolCalls = turnToolCalls(turn);
    final int inputTokens = turnInputTokens(turn);
    final int outputTokens = turnOutputTokens(turn);
    final boolean hasToolCalls = !toolCalls.isEmpty();

    final StringBuilder body = new StringBuilder();

    writeEvent(body, "message_start", messageStartEvent(id, inputTokens));

    int index = 0;
    if (text != null && !text.isBlank()) {
      writeTextBlock(body, index, text);
      index++;
    }
    for (final ToolCallStub toolCall : toolCalls) {
      writeToolUseBlock(body, index, toolCall);
      index++;
    }

    writeEvent(body, "message_delta", messageDeltaEvent(hasToolCalls, inputTokens, outputTokens));
    writeEvent(body, "message_stop", BetaRawMessageStopEvent.builder().build());

    return body.toString();
  }

  /**
   * A server-tool-use turn's data, in the shape Anthropic returns for a Skills/code-execution turn:
   * assistant text, a {@code server_tool_use} (code_execution) call, its {@code
   * code_execution_tool_result}, and trailing text - all in one assistant message, ending the turn
   * with {@code stop_reason: end_turn} (never {@code tool_use}: server-tool blocks are resolved
   * server-side by Anthropic, not dispatched back to the caller as a client tool call).
   */
  record ServerToolUseTurnStub(
      String precedingText,
      String serverToolUseId,
      String codeInputJson,
      String stdout,
      String followingText,
      int inputTokens,
      int outputTokens) {}

  private static String serverToolUseSseBody(ServerToolUseTurnStub turn) {
    final int id = TURN_COUNTER.getAndIncrement();
    final StringBuilder body = new StringBuilder();

    writeEvent(body, "message_start", messageStartEvent(id, turn.inputTokens()));

    int index = 0;
    writeTextBlock(body, index++, turn.precedingText());
    writeServerToolUseBlock(body, index++, turn.serverToolUseId(), turn.codeInputJson());
    writeCodeExecutionToolResultBlock(body, index++, turn.serverToolUseId(), turn.stdout());
    writeTextBlock(body, index++, turn.followingText());

    // Never tool_use: server-tool blocks are resolved server-side, not a client-dispatched call.
    writeEvent(
        body, "message_delta", messageDeltaEvent(false, turn.inputTokens(), turn.outputTokens()));
    writeEvent(body, "message_stop", BetaRawMessageStopEvent.builder().build());

    return body.toString();
  }

  /**
   * Frames a {@code server_tool_use} block exactly like {@link #writeToolUseBlock} frames a client
   * {@code tool_use} block: the vendor SDK's {@code BetaMessageAccumulator} tracks both via the
   * same {@code input_json_delta} accumulation path ({@code tracksToolInput()} covers {@code
   * isToolUse() || isServerToolUse() || isMcpToolUse()}), so the {@code content_block_start} seeds
   * an empty {@code input} and the real argument JSON streams as a delta before {@code
   * content_block_stop} finalizes it. {@code codeInputJson} must be a valid JSON object (not
   * empty/missing) - an empty {@code input_json_delta} finalizes to Anthropic's no-argument {@code
   * JsonMissing} sentinel, which is the shape the earlier {@code JsonMissing} crash this suite
   * guards against was triggered by.
   */
  private static void writeServerToolUseBlock(
      StringBuilder body, int index, String id, String codeInputJson) {
    writeEvent(
        body,
        "content_block_start",
        BetaRawContentBlockStartEvent.builder()
            .contentBlock(
                BetaServerToolUseBlock.builder()
                    .id(id)
                    .name(BetaServerToolUseBlock.Name.CODE_EXECUTION)
                    .caller(BetaDirectCaller.builder().build())
                    .input(JsonValue.from(Map.of()))
                    .build())
            .index(index)
            .build());
    writeEvent(
        body,
        "content_block_delta",
        BetaRawContentBlockDeltaEvent.builder().inputJsonDelta(codeInputJson).index(index).build());
    writeEvent(
        body, "content_block_stop", BetaRawContentBlockStopEvent.builder().index(index).build());
  }

  /**
   * Frames a {@code code_execution_tool_result} block fully formed at {@code content_block_start}
   * (no delta needed): unlike {@code server_tool_use}, {@code tracksToolInput()} does not cover
   * this block type, so the accumulator seeds it directly from the start event's content block and
   * {@code content_block_stop} is a no-op check.
   */
  private static void writeCodeExecutionToolResultBlock(
      StringBuilder body, int index, String toolUseId, String stdout) {
    writeEvent(
        body,
        "content_block_start",
        BetaRawContentBlockStartEvent.builder()
            .contentBlock(
                BetaCodeExecutionToolResultBlock.builder()
                    .toolUseId(toolUseId)
                    .content(
                        BetaCodeExecutionResultBlock.builder()
                            .content(List.of())
                            .returnCode(0L)
                            .stderr("")
                            .stdout(stdout)
                            .build())
                    .build())
            .index(index)
            .build());
    writeEvent(
        body, "content_block_stop", BetaRawContentBlockStopEvent.builder().index(index).build());
  }

  private static BetaRawMessageStartEvent messageStartEvent(int id, int inputTokens) {
    final BetaMessage message =
        BetaMessage.builder()
            .id("msg-test-sse-%s".formatted(id))
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
                    .inputTokens(inputTokens)
                    .outputTokens(0)
                    .cacheCreation((BetaCacheCreation) null)
                    .cacheCreationInputTokens((Long) null)
                    .cacheReadInputTokens((Long) null)
                    .inferenceGeo((String) null)
                    .iterations(List.of())
                    .outputTokensDetails((BetaOutputTokensDetails) null)
                    .serverToolUse((BetaServerToolUsage) null)
                    .serviceTier((ServiceTier) null)
                    .speed((BetaUsage.Speed) null)
                    .build())
            .build();
    return BetaRawMessageStartEvent.builder().message(message).build();
  }

  private static void writeTextBlock(StringBuilder body, int index, String text) {
    writeEvent(
        body,
        "content_block_start",
        BetaRawContentBlockStartEvent.builder()
            .contentBlock(BetaTextBlock.builder().text("").citations(List.of()).build())
            .index(index)
            .build());
    writeEvent(
        body,
        "content_block_delta",
        BetaRawContentBlockDeltaEvent.builder().textDelta(text).index(index).build());
    writeEvent(
        body, "content_block_stop", BetaRawContentBlockStopEvent.builder().index(index).build());
  }

  private static void writeToolUseBlock(StringBuilder body, int index, ToolCallStub toolCall) {
    writeEvent(
        body,
        "content_block_start",
        BetaRawContentBlockStartEvent.builder()
            .contentBlock(
                BetaToolUseBlock.builder()
                    .id(toolCall.id())
                    .name(toolCall.name())
                    .caller(BetaDirectCaller.builder().build())
                    .input(JsonValue.from(Map.of()))
                    .build())
            .index(index)
            .build());
    writeEvent(
        body,
        "content_block_delta",
        BetaRawContentBlockDeltaEvent.builder()
            .inputJsonDelta(toolCall.argumentsJson())
            .index(index)
            .build());
    writeEvent(
        body, "content_block_stop", BetaRawContentBlockStopEvent.builder().index(index).build());
  }

  private static BetaRawMessageDeltaEvent messageDeltaEvent(
      boolean hasToolCalls, int inputTokens, int outputTokens) {
    return BetaRawMessageDeltaEvent.builder()
        .contextManagement((BetaContextManagementResponse) null)
        .delta(
            BetaRawMessageDeltaEvent.Delta.builder()
                .container((BetaContainer) null)
                .stopReason(hasToolCalls ? BetaStopReason.TOOL_USE : BetaStopReason.END_TURN)
                .stopDetails((BetaRefusalStopDetails) null)
                .stopSequence((String) null)
                .build())
        .usage(
            BetaMessageDeltaUsage.builder()
                .cacheCreationInputTokens((Long) null)
                .cacheReadInputTokens((Long) null)
                .inputTokens(inputTokens)
                .iterations(List.of())
                .outputTokens(outputTokens)
                .outputTokensDetails((BetaOutputTokensDetails) null)
                .serverToolUse((BetaServerToolUsage) null)
                .build())
        .build();
  }

  private static void writeEvent(StringBuilder body, String eventName, Object event) {
    try {
      body.append("event: ")
          .append(eventName)
          .append('\n')
          .append("data: ")
          .append(JSON_MAPPER.writeValueAsString(event))
          .append("\n\n");
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private static String turnText(TurnStub turn) {
    return switch (turn) {
      case TurnStub.Text text -> text.text();
      case TurnStub.ToolCalls toolCalls -> toolCalls.text();
    };
  }

  private static List<ToolCallStub> turnToolCalls(TurnStub turn) {
    return switch (turn) {
      case TurnStub.Text text -> List.of();
      case TurnStub.ToolCalls toolCalls -> toolCalls.toolCalls();
    };
  }

  private static int turnInputTokens(TurnStub turn) {
    return switch (turn) {
      case TurnStub.Text text -> text.inputTokens();
      case TurnStub.ToolCalls toolCalls -> toolCalls.inputTokens();
    };
  }

  private static int turnOutputTokens(TurnStub turn) {
    return switch (turn) {
      case TurnStub.Text text -> text.outputTokens();
      case TurnStub.ToolCalls toolCalls -> toolCalls.outputTokens();
    };
  }
}
