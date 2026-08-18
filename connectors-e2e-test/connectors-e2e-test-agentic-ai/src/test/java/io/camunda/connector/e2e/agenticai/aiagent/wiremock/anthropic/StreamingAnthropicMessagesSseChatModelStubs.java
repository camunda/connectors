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
import com.anthropic.models.messages.RedactedThinkingBlock;
import com.anthropic.models.messages.RefusalStopDetails;
import com.anthropic.models.messages.ServerToolUsage;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.ThinkingBlock;
import com.anthropic.models.messages.ThinkingDelta;
import com.anthropic.models.messages.ToolUseBlock;
import com.anthropic.models.messages.Usage;
import com.anthropic.models.messages.Usage.ServiceTier;
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
 * /v1/messages}, requested with {@code Accept: text/event-stream} and {@code "stream": true}) with
 * real Server-Sent-Events framing.
 *
 * <p>Each event is built using the vendor SDK's own {@code RawMessageStreamEvent} member types
 * (rather than hand-rolled JSON) and serialized with the SDK's own {@link
 * ObjectMappers#jsonMapper()}, so the bytes are guaranteed to parse exactly as real Anthropic would
 * send them. The per-turn data (assistant text, tool_use calls, input/output token usage, stop
 * reason) mirrors {@link AnthropicMessagesChatModelStubs.Turn} exactly, just framed as SSE instead
 * of one buffered JSON object:
 *
 * <ol>
 *   <li>{@code message_start} - a {@link Message} shell (id/type/role=assistant/model, empty
 *       content array, {@code usage.input_tokens} set, {@code stop_reason} explicitly {@code
 *       null}).
 *   <li>Per content block (text first, then each tool call, matching the buffered stub's ordering):
 *       {@code content_block_start} (a {@link TextBlock} with empty {@code text}, or a {@link
 *       ToolUseBlock} with empty {@code input}) + one {@code content_block_delta} ({@code
 *       text_delta} carrying the full text, or {@code input_json_delta} carrying the full arguments
 *       JSON in one chunk) + {@code content_block_stop}.
 *   <li>{@code message_delta} - {@code stop_reason} ({@code tool_use} if there were tool calls,
 *       else {@code end_turn}) plus the final {@code usage.output_tokens}.
 *   <li>{@code message_stop}.
 * </ol>
 */
public final class StreamingAnthropicMessagesSseChatModelStubs {

  private static final String SCENARIO_NAME = "llm-conversation-sse";
  private static final JsonMapper JSON_MAPPER = ObjectMappers.jsonMapper();
  private static final AtomicInteger TURN_COUNTER = new AtomicInteger(0);

  private StreamingAnthropicMessagesSseChatModelStubs() {}

  /** Wires the scenario chain returning each turn's SSE response in order. */
  public static void stubConversation(TurnStub... turns) {
    if (turns.length == 0) {
      throw new IllegalArgumentException("At least one conversation turn is required");
    }
    stubScenario(
        Arrays.stream(turns).map(StreamingAnthropicMessagesSseChatModelStubs::sseBody).toList());
  }

  /**
   * A turn whose response leads with a real, streamed {@code thinking} block (content_block_start
   * &rarr; {@code thinking_delta} &rarr; {@code signature_delta} &rarr; content_block_stop)
   * followed by one or more client {@code tool_use} blocks - the shape a reasoning-enabled model
   * returns when it thinks before calling a tool. Always ends the turn with {@code stop_reason:
   * tool_use} (there is always at least one tool call), unlike {@link #sseBody(TurnStub)} which
   * derives the stop reason from whether tool calls are present.
   */
  public record ThinkingTurnStub(
      String thinking,
      String signature,
      List<ToolCallStub> toolCalls,
      int inputTokens,
      int outputTokens) {}

  /**
   * Wires a scenario chain whose first turn is a {@link ThinkingTurnStub} (signed thinking block
   * plus tool_use), followed by any number of ordinary {@link TurnStub} turns.
   */
  public static void stubThinkingConversation(
      ThinkingTurnStub thinkingTurn, TurnStub... followUpTurns) {
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

    // stop_reason is always tool_use here: this stub always carries at least one tool call.
    writeEvent(
        body, "message_delta", messageDeltaEvent(true, turn.inputTokens(), turn.outputTokens()));
    writeEvent(body, "message_stop", RawMessageStopEvent.builder().build());

    return body.toString();
  }

  /**
   * Frames a {@code thinking} block the same way real Anthropic streams it: an empty shell at
   * {@code content_block_start}, the thinking text streamed via a {@code thinking_delta}, then the
   * cryptographic signature streamed via a {@code signature_delta} - both accumulated by the vendor
   * SDK's {@code MessageAccumulator} onto the same block before {@code content_block_stop}
   * finalizes it, exactly like {@link #writeToolUseBlock}'s {@code input_json_delta} accumulation.
   */
  private static void writeThinkingBlock(
      StringBuilder body, int index, String thinking, String signature) {
    writeEvent(
        body,
        "content_block_start",
        RawContentBlockStartEvent.builder()
            .contentBlock(ThinkingBlock.builder().thinking("").signature("").build())
            .index(index)
            .build());
    writeEvent(
        body,
        "content_block_delta",
        RawContentBlockDeltaEvent.builder()
            .delta(ThinkingDelta.builder().thinking(thinking).build())
            .index(index)
            .build());
    writeEvent(
        body,
        "content_block_delta",
        RawContentBlockDeltaEvent.builder().signatureDelta(signature).index(index).build());
    writeEvent(body, "content_block_stop", RawContentBlockStopEvent.builder().index(index).build());
  }

  /**
   * A turn whose response leads with a real {@code redacted_thinking} block - fully formed at
   * {@code content_block_start} with its opaque {@code data} already populated, unlike a plain
   * {@code thinking} block, since real Anthropic streams a redacted block whole with no {@code
   * thinking}/{@code signature} deltas - followed by one or more client {@code tool_use} blocks.
   * Always ends the turn with {@code stop_reason: tool_use} (there is always at least one tool
   * call), mirroring {@link ThinkingTurnStub}.
   */
  public record RedactedThinkingTurnStub(
      String data, List<ToolCallStub> toolCalls, int inputTokens, int outputTokens) {}

  /**
   * Wires a scenario chain whose first turn is a {@link RedactedThinkingTurnStub} (redacted
   * thinking block plus tool_use), followed by any number of ordinary {@link TurnStub} turns -
   * mirrors {@link #stubThinkingConversation(ThinkingTurnStub, TurnStub...)}, just for a redacted
   * thinking block instead of a signed one.
   */
  public static void stubRedactedThinkingConversation(
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

    // stop_reason is always tool_use here: this stub always carries at least one tool call.
    writeEvent(
        body, "message_delta", messageDeltaEvent(true, turn.inputTokens(), turn.outputTokens()));
    writeEvent(body, "message_stop", RawMessageStopEvent.builder().build());

    return body.toString();
  }

  /**
   * Frames a {@code redacted_thinking} block the way real Anthropic streams it: fully formed
   * already at {@code content_block_start} (opaque {@code data} populated) with no deltas at all -
   * unlike {@link #writeThinkingBlock}'s {@code thinking_delta}/{@code signature_delta}
   * accumulation. There is no delta variant for a redacted-thinking block (the SDK defines {@code
   * TextDelta}, {@code InputJsonDelta}, {@code ThinkingDelta}, {@code SignatureDelta} and {@code
   * CitationsDelta}, but none for {@code redacted_thinking}), so it is always sent whole.
   */
  private static void writeRedactedThinkingBlock(StringBuilder body, int index, String data) {
    writeEvent(
        body,
        "content_block_start",
        RawContentBlockStartEvent.builder()
            .contentBlock(RedactedThinkingBlock.builder().data(data).build())
            .index(index)
            .build());
    writeEvent(body, "content_block_stop", RawContentBlockStopEvent.builder().index(index).build());
  }

  /**
   * A turn whose response leads with assistant text but ends with a {@code refusal} stop reason -
   * the shape Anthropic returns when its content filtering blocks the response after some text was
   * already generated. Always ends the turn with {@code stop_reason: refusal}, unlike {@link
   * #sseBody(TurnStub)} which derives the stop reason from whether tool calls are present.
   */
  public record RefusalTurnStub(String text, int inputTokens, int outputTokens) {}

  /** Wires a single-turn scenario whose response ends with a {@code refusal} stop reason. */
  public static void stubRefusalConversation(RefusalTurnStub refusalTurn) {
    stubScenario(List.of(refusalSseBody(refusalTurn)));
  }

  private static String refusalSseBody(RefusalTurnStub turn) {
    final int id = TURN_COUNTER.getAndIncrement();
    final StringBuilder body = new StringBuilder();

    writeEvent(body, "message_start", messageStartEvent(id, turn.inputTokens()));
    writeTextBlock(body, 0, turn.text());
    writeEvent(
        body,
        "message_delta",
        messageDeltaEvent(StopReason.REFUSAL, turn.inputTokens(), turn.outputTokens()));
    writeEvent(body, "message_stop", RawMessageStopEvent.builder().build());

    return body.toString();
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
    writeEvent(body, "message_stop", RawMessageStopEvent.builder().build());

    return body.toString();
  }

  private static RawMessageStartEvent messageStartEvent(int id, int inputTokens) {
    final Message message =
        Message.builder()
            .id("msg-test-sse-%s".formatted(id))
            .container((Container) null)
            .content(List.of())
            .model("test-model")
            .stopDetails((RefusalStopDetails) null)
            .stopReason((StopReason) null)
            .stopSequence((String) null)
            .usage(
                Usage.builder()
                    .inputTokens(inputTokens)
                    .outputTokens(0)
                    .cacheCreation((CacheCreation) null)
                    .cacheCreationInputTokens((Long) null)
                    .cacheReadInputTokens((Long) null)
                    .inferenceGeo((String) null)
                    .outputTokensDetails((OutputTokensDetails) null)
                    .serverToolUse((ServerToolUsage) null)
                    .serviceTier((ServiceTier) null)
                    .build())
            .build();
    return RawMessageStartEvent.builder().message(message).build();
  }

  private static void writeTextBlock(StringBuilder body, int index, String text) {
    writeEvent(
        body,
        "content_block_start",
        RawContentBlockStartEvent.builder()
            .contentBlock(TextBlock.builder().text("").citations(List.of()).build())
            .index(index)
            .build());
    writeEvent(
        body,
        "content_block_delta",
        RawContentBlockDeltaEvent.builder().textDelta(text).index(index).build());
    writeEvent(body, "content_block_stop", RawContentBlockStopEvent.builder().index(index).build());
  }

  private static void writeToolUseBlock(StringBuilder body, int index, ToolCallStub toolCall) {
    writeEvent(
        body,
        "content_block_start",
        RawContentBlockStartEvent.builder()
            .contentBlock(
                ToolUseBlock.builder()
                    .id(toolCall.id())
                    .name(toolCall.name())
                    .caller(DirectCaller.builder().build())
                    .input(JsonValue.from(Map.of()))
                    .build())
            .index(index)
            .build());
    writeEvent(
        body,
        "content_block_delta",
        RawContentBlockDeltaEvent.builder()
            .inputJsonDelta(toolCall.argumentsJson())
            .index(index)
            .build());
    writeEvent(body, "content_block_stop", RawContentBlockStopEvent.builder().index(index).build());
  }

  private static RawMessageDeltaEvent messageDeltaEvent(
      boolean hasToolCalls, int inputTokens, int outputTokens) {
    return messageDeltaEvent(
        hasToolCalls ? StopReason.TOOL_USE : StopReason.END_TURN, inputTokens, outputTokens);
  }

  private static RawMessageDeltaEvent messageDeltaEvent(
      StopReason stopReason, int inputTokens, int outputTokens) {
    return RawMessageDeltaEvent.builder()
        .delta(
            RawMessageDeltaEvent.Delta.builder()
                .container((Container) null)
                .stopReason(stopReason)
                .stopDetails((RefusalStopDetails) null)
                .stopSequence((String) null)
                .build())
        .usage(
            MessageDeltaUsage.builder()
                .cacheCreationInputTokens((Long) null)
                .cacheReadInputTokens((Long) null)
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .outputTokensDetails((OutputTokensDetails) null)
                .serverToolUse((ServerToolUsage) null)
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
      case TurnStub.ToolCalls toolCalls -> toolCalls.toolCalls();
      default -> List.of();
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
