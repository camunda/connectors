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
package io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.ScenarioMappingBuilder;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.openai.core.ObjectMappers;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCompletedEvent;
import com.openai.models.responses.ResponseStreamEvent;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.ToolCallStub;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.TurnStub;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stubs the OpenAI Responses endpoint's streaming response ({@code POST /v1/responses}, {@code
 * stream: true}) with real Server-Sent-Events framing, for the native (own-LLM-layer) OpenAI
 * provider, which always drives {@code client.responses().createStreaming(params)} and feeds the
 * events to the vendor SDK's {@code ResponseAccumulator}.
 *
 * <p>Unlike the Chat Completions accumulator (which needs the full delta chunk sequence), {@code
 * ResponseAccumulator} only needs a single terminal {@code response.completed} event carrying the
 * full {@link Response} - so each turn's body is built by constructing the whole {@link Response}
 * as JSON, parsing it into an SDK {@link Response} via {@link ObjectMappers#jsonMapper()} (so the
 * shape is guaranteed to match what the real accumulator/response converter expect), wrapping it in
 * a {@code response.completed} {@link ResponseStreamEvent}, and framing that single event as {@code
 * data: <json>\n\n} followed by {@code data: [DONE]\n\n}.
 *
 * <p>Each turn's {@code output[]} array carries an optional {@code message} item (present whenever
 * the turn has non-blank assistant text - matching {@code OpenAiResponsesResponseConverter}, which
 * only produces {@link io.camunda.connector.agenticai.aiagent.model.message.content.TextContent}
 * for an {@code output_text} part) followed by one {@code function_call} item per tool call.
 */
final class NativeOpenAiResponsesSseChatModelStubs {

  static final String RESPONSES_PATH = "/v1/responses";

  private static final String SCENARIO_NAME = "llm-conversation-openai-responses-sse";
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final AtomicInteger TURN_COUNTER = new AtomicInteger(0);

  private NativeOpenAiResponsesSseChatModelStubs() {}

  static void stubConversation(TurnStub... turns) {
    if (turns.length == 0) {
      throw new IllegalArgumentException("At least one conversation turn is required");
    }
    final List<String> bodies = new ArrayList<>();
    for (final TurnStub turn : turns) {
      bodies.add(sseBody(turn));
    }
    stubScenario(bodies);
  }

  /**
   * Wires a scenario chain whose first turn is a {@link ReasoningTurnStub} (an {@code encrypted
   * reasoning} item followed by one or more client {@code function_call} items), followed by any
   * number of ordinary {@link TurnStub} turns - mirrors the native-Anthropic sibling's {@code
   * stubThinkingConversation}, just for the Responses reasoning-item shape instead of Anthropic's
   * streamed {@code thinking} block.
   */
  static void stubReasoningConversation(
      ReasoningTurnStub reasoningTurn, TurnStub... followUpTurns) {
    final List<String> bodies = new ArrayList<>();
    bodies.add(reasoningSseBody(reasoningTurn));
    for (final TurnStub turn : followUpTurns) {
      bodies.add(sseBody(turn));
    }
    stubScenario(bodies);
  }

  /**
   * Wires a scenario chain whose first turn is a {@link ServerToolTurnStub} (a {@code
   * web_search_call} server-tool item alongside assistant text), followed by any number of ordinary
   * {@link TurnStub} turns - mirrors the native-Anthropic sibling's {@code
   * stubServerToolUseConversation}, just for OpenAI's {@code web_search_call} item instead of
   * Anthropic's {@code server_tool_use}/{@code code_execution_tool_result} block pair.
   */
  static void stubServerToolConversation(
      ServerToolTurnStub serverToolTurn, TurnStub... followUpTurns) {
    final List<String> bodies = new ArrayList<>();
    bodies.add(serverToolSseBody(serverToolTurn));
    for (final TurnStub turn : followUpTurns) {
      bodies.add(sseBody(turn));
    }
    stubScenario(bodies);
  }

  /** Shared scenario-chaining plumbing: returns each pre-rendered SSE body in order. */
  private static void stubScenario(List<String> bodies) {
    for (int i = 0; i < bodies.size(); i++) {
      final String fromState = i == 0 ? Scenario.STARTED : stateName(i);
      ScenarioMappingBuilder mapping =
          post(urlPathEqualTo(RESPONSES_PATH))
              .inScenario(SCENARIO_NAME)
              .whenScenarioStateIs(fromState)
              .willReturn(sseResponse(bodies.get(i)));
      if (i < bodies.size() - 1) {
        mapping = mapping.willSetStateTo(stateName(i + 1));
      }
      stubFor(mapping);
    }
  }

  private static String sseBody(TurnStub turn) {
    final int id = TURN_COUNTER.getAndIncrement();
    final String text =
        (turn instanceof TurnStub.Text t) ? t.text() : ((TurnStub.ToolCalls) turn).text();
    final List<ToolCallStub> toolCalls =
        (turn instanceof TurnStub.ToolCalls tc) ? tc.toolCalls() : List.of();
    final int inputTokens =
        (turn instanceof TurnStub.Text t)
            ? t.inputTokens()
            : ((TurnStub.ToolCalls) turn).inputTokens();
    final int outputTokens =
        (turn instanceof TurnStub.Text t)
            ? t.outputTokens()
            : ((TurnStub.ToolCalls) turn).outputTokens();

    return frame(responseJson(id, text, toolCalls, inputTokens, outputTokens));
  }

  /**
   * A turn whose response leads with a real {@code reasoning} output item carrying {@code
   * encrypted_content} (the shape gpt-5/Responses returns when {@code
   * configuration.openai.model.parameters.effort} triggers {@code include:
   * ["reasoning.encrypted_content"]}, see {@code OpenAiResponsesRequestConverter#applyReasoning}),
   * followed by one or more client {@code function_call} items - the shape a reasoning-enabled
   * model returns when it reasons before calling a tool.
   *
   * <p>Exists so e2e coverage can prove the Task 4 round-trip end to end through the REAL {@code
   * ResponseAccumulator}: the resulting {@code ReasoningContent}'s raw {@code providerPayload}
   * (captured by {@code OpenAiResponsesResponseConverter#toReasoningContent}) must be replayed
   * byte-identical - same {@code encrypted_content} value, positioned before the function_call item
   * - on the follow-up model call once the tool result is available (see {@code
   * OpenAiResponsesRequestConverter#assistantInputItems}).
   */
  record ReasoningTurnStub(
      String reasoningId,
      String encryptedContent,
      List<ToolCallStub> toolCalls,
      int inputTokens,
      int outputTokens) {}

  private static String reasoningSseBody(ReasoningTurnStub turn) {
    final int id = TURN_COUNTER.getAndIncrement();
    final StringBuilder output = new StringBuilder("[");
    output.append(reasoningItemJson(turn.reasoningId(), turn.encryptedContent()));
    for (int i = 0; i < turn.toolCalls().size(); i++) {
      output.append(',').append(functionCallItemJson(id, i, turn.toolCalls().get(i)));
    }
    output.append(']');

    return frame(
        "{\"id\":"
            + quote("resp-test-" + id)
            + ",\"object\":\"response\",\"created_at\":0,\"model\":\"test-model\","
            + "\"status\":\"completed\",\"parallel_tool_calls\":true,\"tool_choice\":\"auto\","
            + "\"tools\":[],\"output\":"
            + output
            + ",\"usage\":"
            + usageJson(turn.inputTokens(), turn.outputTokens())
            + "}");
  }

  private static String reasoningItemJson(String id, String encryptedContent) {
    return "{\"type\":\"reasoning\",\"id\":"
        + quote(id)
        + ",\"encrypted_content\":"
        + quote(encryptedContent)
        + ",\"summary\":[]}";
  }

  /**
   * A turn whose response contains a {@code web_search_call} server-tool item alongside assistant
   * text - the shape a real {@code enableWebSearch}-enabled turn returns. Unlike a client {@code
   * function_call}, a server-tool item is resolved server-side by OpenAI itself and is never
   * dispatched back to the caller, so this turn carries no client tool call at all - matching how
   * {@code OpenAiResponsesResponseConverter} captures it as {@code ProviderContent}, never {@code
   * toolCalls}.
   */
  record ServerToolTurnStub(
      String text, String webSearchCallId, String searchQuery, int inputTokens, int outputTokens) {}

  private static String serverToolSseBody(ServerToolTurnStub turn) {
    final int id = TURN_COUNTER.getAndIncrement();
    final StringBuilder output = new StringBuilder("[");
    boolean first = true;
    if (turn.text() != null && !turn.text().isBlank()) {
      output.append(messageItemJson(id, turn.text()));
      first = false;
    }
    if (!first) {
      output.append(',');
    }
    output.append(webSearchCallItemJson(turn.webSearchCallId(), turn.searchQuery()));
    output.append(']');

    return frame(
        "{\"id\":"
            + quote("resp-test-" + id)
            + ",\"object\":\"response\",\"created_at\":0,\"model\":\"test-model\","
            + "\"status\":\"completed\",\"parallel_tool_calls\":true,\"tool_choice\":\"auto\","
            + "\"tools\":[],\"output\":"
            + output
            + ",\"usage\":"
            + usageJson(turn.inputTokens(), turn.outputTokens())
            + "}");
  }

  private static String webSearchCallItemJson(String id, String searchQuery) {
    return "{\"type\":\"web_search_call\",\"id\":"
        + quote(id)
        + ",\"status\":\"completed\",\"action\":{\"type\":\"search\",\"query\":"
        + quote(searchQuery)
        + "}}";
  }

  /**
   * Parses the given response JSON into an SDK {@link Response}, wraps it in a {@code
   * response.completed} {@link ResponseStreamEvent}, and frames it as SSE - the shared tail of
   * every turn-body builder in this class (see the class-level Javadoc).
   */
  private static String frame(String responseJson) {
    final Response response = parseResponse(responseJson);
    final ResponseStreamEvent event =
        ResponseStreamEvent.ofCompleted(
            ResponseCompletedEvent.builder().response(response).sequenceNumber(0L).build());
    return "data: " + serialize(event) + "\n\ndata: [DONE]\n\n";
  }

  private static String responseJson(
      int id, String text, List<ToolCallStub> toolCalls, int inputTokens, int outputTokens) {
    final StringBuilder output = new StringBuilder("[");
    boolean first = true;
    if (text != null && !text.isBlank()) {
      output.append(messageItemJson(id, text));
      first = false;
    }
    for (int i = 0; i < toolCalls.size(); i++) {
      if (!first) {
        output.append(',');
      }
      output.append(functionCallItemJson(id, i, toolCalls.get(i)));
      first = false;
    }
    output.append(']');

    return "{\"id\":"
        + quote("resp-test-" + id)
        + ",\"object\":\"response\",\"created_at\":0,\"model\":\"test-model\","
        + "\"status\":\"completed\",\"parallel_tool_calls\":true,\"tool_choice\":\"auto\","
        + "\"tools\":[],\"output\":"
        + output
        + ",\"usage\":"
        + usageJson(inputTokens, outputTokens)
        + "}";
  }

  private static String messageItemJson(int id, String text) {
    return "{\"type\":\"message\",\"id\":"
        + quote("msg-test-" + id)
        + ",\"role\":\"assistant\",\"status\":\"completed\",\"content\":[{\"type\":\"output_text\","
        + "\"text\":"
        + quote(text)
        + ",\"annotations\":[]}]}";
  }

  private static String functionCallItemJson(int id, int index, ToolCallStub toolCall) {
    return "{\"type\":\"function_call\",\"id\":"
        + quote("fc-test-" + id + "-" + index)
        + ",\"call_id\":"
        + quote(toolCall.id())
        + ",\"name\":"
        + quote(toolCall.name())
        + ",\"arguments\":"
        + quote(toolCall.argumentsJson())
        + ",\"status\":\"completed\"}";
  }

  private static String usageJson(int inputTokens, int outputTokens) {
    return "{\"input_tokens\":"
        + inputTokens
        + ",\"output_tokens\":"
        + outputTokens
        + ",\"total_tokens\":"
        + (inputTokens + outputTokens)
        + ",\"input_tokens_details\":{\"cached_tokens\":0},"
        + "\"output_tokens_details\":{\"reasoning_tokens\":0}}";
  }

  private static Response parseResponse(String json) {
    try {
      return ObjectMappers.jsonMapper().readValue(json, Response.class);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private static String serialize(ResponseStreamEvent event) {
    try {
      return ObjectMappers.jsonMapper().writeValueAsString(event);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private static String quote(String raw) {
    try {
      return JSON.writeValueAsString(raw);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private static ResponseDefinitionBuilder sseResponse(String body) {
    return aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "text/event-stream")
        .withBody(body);
  }

  private static String stateName(int index) {
    return "turn-" + index;
  }
}
