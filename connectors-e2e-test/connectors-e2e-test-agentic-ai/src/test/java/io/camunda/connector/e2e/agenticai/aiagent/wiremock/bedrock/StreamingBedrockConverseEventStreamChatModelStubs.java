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
package io.camunda.connector.e2e.agenticai.aiagent.wiremock.bedrock;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.ScenarioMappingBuilder;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.ToolCallStub;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.TurnStub;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import software.amazon.eventstream.HeaderValue;
import software.amazon.eventstream.Message;
import software.amazon.eventstream.MessageBuilder;

/**
 * Stubs the AWS Bedrock {@code ConverseStream} endpoint ({@code POST
 * /model/test-model/converse-stream}) using real AWS EventStream binary framing, so the native v2
 * Bedrock provider drives its conversation loop against a mock HTTP endpoint.
 *
 * <p>Each event is a JSON payload matching the vendor SDK's own event POJOs' wire field names
 * (verified against the {@code bedrockruntime} model classes' {@code SdkField} location names, not
 * guessed), framed via {@link software.amazon.eventstream.MessageBuilder} - the same library class
 * the AWS SDK's own {@code EventStreamAsyncResponseTransformer} decodes frames with - so the bytes
 * parse exactly as real Bedrock would send them. The per-turn data is framed as delta-based events.
 */
public final class StreamingBedrockConverseEventStreamChatModelStubs {

  public static final String CONVERSE_PATH = "/model/test-model/converse";
  public static final String CONVERSE_STREAM_PATH = CONVERSE_PATH + "-stream";

  private static final String SCENARIO_NAME = "llm-conversation-eventstream";
  private static final String CONTENT_TYPE_EVENTSTREAM = "application/vnd.amazon.eventstream";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private StreamingBedrockConverseEventStreamChatModelStubs() {}

  /** Wires the scenario chain returning each turn's EventStream response in order. */
  public static void stubConversation(TurnStub... turns) {
    if (turns.length == 0) {
      throw new IllegalArgumentException("At least one conversation turn is required");
    }
    stubScenario(
        Arrays.stream(turns)
            .map(StreamingBedrockConverseEventStreamChatModelStubs::eventStreamBody)
            .toList());
  }

  /**
   * A turn whose response leads with a real, streamed {@code reasoningContent} block ({@code
   * contentBlockStart} &rarr; {@code text} delta &rarr; {@code signature} delta &rarr; {@code
   * contentBlockStop}) followed by one or more client {@code toolUse} blocks - the shape a
   * reasoning-enabled model returns when it thinks before calling a tool, and the deterministic
   * proof that the cryptographic signature survives the streamed round-trip. Always ends the turn
   * with {@code stopReason: tool_use} (there is always at least one tool call), unlike {@link
   * #eventStreamBody(TurnStub)} which derives the stop reason from whether tool calls are present.
   */
  public record ReasoningTurnStub(
      String reasoningText,
      String signature,
      List<ToolCallStub> toolCalls,
      int inputTokens,
      int outputTokens) {}

  /**
   * Wires a scenario chain whose first turn is a {@link ReasoningTurnStub} (signed reasoning block
   * plus tool_use), followed by any number of ordinary {@link TurnStub} turns.
   */
  public static void stubReasoningConversation(
      ReasoningTurnStub reasoningTurn, TurnStub... followUpTurns) {
    final List<byte[]> bodies = new ArrayList<>();
    bodies.add(reasoningEventStreamBody(reasoningTurn));
    for (final TurnStub turn : followUpTurns) {
      bodies.add(eventStreamBody(turn));
    }
    stubScenario(bodies);
  }

  private static byte[] reasoningEventStreamBody(ReasoningTurnStub turn) {
    final ByteArrayOutputStream body = new ByteArrayOutputStream();

    writeEvent(body, "messageStart", Map.of("role", "assistant"));

    int index = 0;
    writeReasoningContentBlock(body, index++, turn.reasoningText(), turn.signature());
    for (final ToolCallStub toolCall : turn.toolCalls()) {
      writeToolUseBlock(body, index++, toolCall);
    }

    // stopReason is always tool_use here: this stub always carries at least one tool call.
    writeEvent(body, "messageStop", Map.of("stopReason", "tool_use"));
    writeEvent(body, "metadata", metadataPayload(turn.inputTokens(), turn.outputTokens()));

    return body.toByteArray();
  }

  /**
   * Frames a {@code reasoningContent} block the way real Bedrock streams it: an empty shell at
   * {@code contentBlockStart} (no {@code start} payload at all - unlike a tool-use block, {@code
   * ContentBlockStart} has no {@code reasoningContent} union member), the reasoning text streamed
   * via a {@code reasoningContent.text} delta, then the cryptographic signature streamed via a
   * {@code reasoningContent.signature} delta - both accumulated by {@code
   * BedrockConverseStreamAssembler} onto the same block before {@code contentBlockStop} finalizes
   * it, exactly like {@link #writeToolUseBlock}'s {@code toolUse.input} accumulation.
   */
  private static void writeReasoningContentBlock(
      ByteArrayOutputStream body, int index, String reasoningText, String signature) {
    writeEvent(body, "contentBlockStart", Map.of("contentBlockIndex", index));
    writeEvent(
        body,
        "contentBlockDelta",
        Map.of(
            "contentBlockIndex",
            index,
            "delta",
            Map.of("reasoningContent", Map.of("text", reasoningText))));
    writeEvent(
        body,
        "contentBlockDelta",
        Map.of(
            "contentBlockIndex",
            index,
            "delta",
            Map.of("reasoningContent", Map.of("signature", signature))));
    writeEvent(body, "contentBlockStop", Map.of("contentBlockIndex", index));
  }

  /** A turn whose response ends with a {@code content_filtered} stop reason. */
  public record ContentFilteredTurnStub(String text, int inputTokens, int outputTokens) {}

  /**
   * Wires a single-turn scenario whose response ends with a {@code content_filtered} stop reason.
   */
  public static void stubContentFilteredConversation(ContentFilteredTurnStub turn) {
    stubScenario(List.of(contentFilteredEventStreamBody(turn)));
  }

  private static byte[] contentFilteredEventStreamBody(ContentFilteredTurnStub turn) {
    final ByteArrayOutputStream body = new ByteArrayOutputStream();

    writeEvent(body, "messageStart", Map.of("role", "assistant"));
    writeTextBlock(body, 0, turn.text());
    writeEvent(body, "messageStop", Map.of("stopReason", "content_filtered"));
    writeEvent(body, "metadata", metadataPayload(turn.inputTokens(), turn.outputTokens()));

    return body.toByteArray();
  }

  /**
   * Wires a single-turn scenario whose response is delayed by {@code delay} via WireMock's {@code
   * withFixedDelay} - used by HTTP-transport-timeout e2e coverage to simulate a slow/hanging model
   * response on the native EventStream endpoint.
   */
  public static void stubConversation(Duration delay, TurnStub turn) {
    stubFor(
        post(urlPathEqualTo(CONVERSE_STREAM_PATH))
            .willReturn(
                eventStreamResponse(eventStreamBody(turn)).withFixedDelay((int) delay.toMillis())));
  }

  /** Shared scenario-chaining plumbing: returns each pre-rendered EventStream body in order. */
  private static void stubScenario(List<byte[]> bodies) {
    for (int i = 0; i < bodies.size(); i++) {
      final String fromState = i == 0 ? Scenario.STARTED : stateName(i);

      ScenarioMappingBuilder mapping =
          post(urlPathEqualTo(CONVERSE_STREAM_PATH))
              .inScenario(SCENARIO_NAME)
              .whenScenarioStateIs(fromState)
              .willReturn(eventStreamResponse(bodies.get(i)));

      if (i < bodies.size() - 1) {
        mapping = mapping.willSetStateTo(stateName(i + 1));
      }

      stubFor(mapping);
    }
  }

  private static String stateName(int index) {
    return "turn-" + index;
  }

  private static ResponseDefinitionBuilder eventStreamResponse(byte[] body) {
    return aResponse()
        .withStatus(200)
        .withHeader("Content-Type", CONTENT_TYPE_EVENTSTREAM)
        .withBody(body);
  }

  private static byte[] eventStreamBody(TurnStub turn) {
    final String text = turnText(turn);
    final List<ToolCallStub> toolCalls = turnToolCalls(turn);
    final int inputTokens = turnInputTokens(turn);
    final int outputTokens = turnOutputTokens(turn);
    final boolean hasToolCalls = !toolCalls.isEmpty();

    final ByteArrayOutputStream body = new ByteArrayOutputStream();

    writeEvent(body, "messageStart", Map.of("role", "assistant"));

    int index = 0;
    if (text != null && !text.isBlank()) {
      writeTextBlock(body, index, text);
      index++;
    }
    for (final ToolCallStub toolCall : toolCalls) {
      writeToolUseBlock(body, index, toolCall);
      index++;
    }

    writeEvent(body, "messageStop", Map.of("stopReason", hasToolCalls ? "tool_use" : "end_turn"));
    writeEvent(body, "metadata", metadataPayload(inputTokens, outputTokens));

    return body.toByteArray();
  }

  private static void writeTextBlock(ByteArrayOutputStream body, int index, String text) {
    writeEvent(body, "contentBlockStart", Map.of("contentBlockIndex", index));
    writeEvent(
        body,
        "contentBlockDelta",
        Map.of("contentBlockIndex", index, "delta", Map.of("text", text)));
    writeEvent(body, "contentBlockStop", Map.of("contentBlockIndex", index));
  }

  private static void writeToolUseBlock(
      ByteArrayOutputStream body, int index, ToolCallStub toolCall) {
    final Map<String, Object> toolUseStart = new LinkedHashMap<>();
    toolUseStart.put("toolUseId", toolCall.id());
    toolUseStart.put("name", toolCall.name());

    writeEvent(
        body,
        "contentBlockStart",
        Map.of("contentBlockIndex", index, "start", Map.of("toolUse", toolUseStart)));
    writeEvent(
        body,
        "contentBlockDelta",
        Map.of(
            "contentBlockIndex",
            index,
            "delta",
            Map.of("toolUse", Map.of("input", toolCall.argumentsJson()))));
    writeEvent(body, "contentBlockStop", Map.of("contentBlockIndex", index));
  }

  private static Map<String, Object> metadataPayload(int inputTokens, int outputTokens) {
    final Map<String, Object> usage = new LinkedHashMap<>();
    usage.put("inputTokens", inputTokens);
    usage.put("outputTokens", outputTokens);
    usage.put("totalTokens", inputTokens + outputTokens);

    final Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("usage", usage);
    metadata.put("metrics", Map.of("latencyMs", 123));
    return metadata;
  }

  /**
   * Serializes {@code payload} to JSON, wraps it as one AWS EventStream frame (prelude + headers +
   * payload + two CRCs) via {@link MessageBuilder}, and appends the encoded frame to {@code body}.
   */
  private static void writeEvent(ByteArrayOutputStream body, String eventType, Object payload) {
    try {
      final byte[] payloadBytes = OBJECT_MAPPER.writeValueAsBytes(payload);

      final Map<String, HeaderValue> headers = new LinkedHashMap<>();
      headers.put(":message-type", HeaderValue.fromString("event"));
      headers.put(":event-type", HeaderValue.fromString(eventType));
      headers.put(":content-type", HeaderValue.fromString("application/json"));

      final Message message = MessageBuilder.defaultBuilder().build(headers, payloadBytes);
      message.encode(body);
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
