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
import com.anthropic.models.beta.messages.BetaRefusalStopDetails;
import com.anthropic.models.beta.messages.BetaServerToolUsage;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.BetaTextBlock;
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

    for (int i = 0; i < turns.length; i++) {
      final String fromState = i == 0 ? Scenario.STARTED : stateName(i);

      ScenarioMappingBuilder mapping =
          post(urlPathEqualTo(MESSAGES_PATH))
              .inScenario(SCENARIO_NAME)
              .whenScenarioStateIs(fromState)
              .willReturn(sseResponse(turns[i]));

      if (i < turns.length - 1) {
        mapping = mapping.willSetStateTo(stateName(i + 1));
      }

      stubFor(mapping);
    }
  }

  private static String stateName(int index) {
    return "turn-" + index;
  }

  private static ResponseDefinitionBuilder sseResponse(TurnStub turn) {
    return aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "text/event-stream")
        .withBody(sseBody(turn));
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
