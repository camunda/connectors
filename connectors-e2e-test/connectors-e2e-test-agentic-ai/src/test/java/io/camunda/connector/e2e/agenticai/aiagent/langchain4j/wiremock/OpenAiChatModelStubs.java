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
package io.camunda.connector.e2e.agenticai.aiagent.langchain4j.wiremock;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.util.Arrays;
import java.util.List;

/**
 * Stubs the OpenAI-compatible chat completions endpoint ({@code POST /v1/chat/completions}) so the
 * real LangChain4j {@code OpenAiChatModel} drives the agent conversation loop against WireMock
 * instead of a Mockito mock.
 *
 * <p>A conversation is expressed as an ordered list of {@link Turn turns}. Each model call advances
 * a WireMock {@link Scenario} state, so sequential calls deterministically receive the next turn's
 * response regardless of request body. This mirrors the previous Mockito {@code mockChatInteractions}
 * queue.
 *
 * <p>The real ad-hoc sub-process tools execute between turns (as before), so tool <em>results</em>
 * are produced by the engine — only the assistant turns (text and tool-call requests) are stubbed
 * here.
 */
public final class OpenAiChatModelStubs {

  public static final String CHAT_COMPLETIONS_PATH = "/v1/chat/completions";

  private static final String SCENARIO_NAME = "llm-conversation";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private OpenAiChatModelStubs() {}

  /** Wires the scenario chain returning each turn's response in order. */
  public static void stubConversation(Turn... turns) {
    final var turnList = Arrays.asList(turns);
    if (turnList.isEmpty()) {
      throw new IllegalArgumentException("At least one conversation turn is required");
    }

    for (int i = 0; i < turnList.size(); i++) {
      final String fromState = i == 0 ? Scenario.STARTED : stateName(i);

      MappingBuilder mapping =
          post(urlPathEqualTo(CHAT_COMPLETIONS_PATH))
              .inScenario(SCENARIO_NAME)
              .whenScenarioStateIs(fromState)
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(turnList.get(i).toResponseJson()));

      // Advance to the next state unless this is the final turn.
      if (i < turnList.size() - 1) {
        mapping = mapping.willSetStateTo(stateName(i + 1));
      }

      stubFor(mapping);
    }
  }

  private static String stateName(int index) {
    return "turn-" + index;
  }

  /** A single assistant turn in the conversation. */
  public static final class Turn {

    private final String text;
    private final List<ToolCall> toolCalls;
    private final int promptTokens;
    private final int completionTokens;

    private Turn(String text, List<ToolCall> toolCalls, int promptTokens, int completionTokens) {
      this.text = text;
      this.toolCalls = toolCalls;
      this.promptTokens = promptTokens;
      this.completionTokens = completionTokens;
    }

    /** A plain text response that ends the turn ({@code finish_reason: "stop"}). */
    public static Turn text(String text, int promptTokens, int completionTokens) {
      return new Turn(text, List.of(), promptTokens, completionTokens);
    }

    /**
     * A tool-call response ({@code finish_reason: "tool_calls"}). The optional assistant text is
     * included alongside the tool calls, matching how providers return reasoning text with tool
     * calls.
     */
    public static Turn toolCalls(
        String text, int promptTokens, int completionTokens, ToolCall... toolCalls) {
      return new Turn(text, Arrays.asList(toolCalls), promptTokens, completionTokens);
    }

    private String toResponseJson() {
      final ObjectNode root = OBJECT_MAPPER.createObjectNode();
      root.put("id", "chatcmpl-test");
      root.put("object", "chat.completion");
      root.put("created", 1700000000);
      root.put("model", "test-model");

      final ObjectNode message = OBJECT_MAPPER.createObjectNode();
      message.put("role", "assistant");
      if (text != null) {
        message.put("content", text);
      } else {
        message.putNull("content");
      }

      if (!toolCalls.isEmpty()) {
        final ArrayNode toolCallsNode = message.putArray("tool_calls");
        for (final ToolCall toolCall : toolCalls) {
          final ObjectNode toolCallNode = toolCallsNode.addObject();
          toolCallNode.put("id", toolCall.id());
          toolCallNode.put("type", "function");
          final ObjectNode function = toolCallNode.putObject("function");
          function.put("name", toolCall.name());
          // arguments must be a JSON-encoded string, not a nested object
          function.put("arguments", toolCall.argumentsJson());
        }
      }

      final ObjectNode choice = OBJECT_MAPPER.createObjectNode();
      choice.put("index", 0);
      choice.set("message", message);
      choice.put("finish_reason", toolCalls.isEmpty() ? "stop" : "tool_calls");
      root.putArray("choices").add(choice);

      final ObjectNode usage = root.putObject("usage");
      usage.put("prompt_tokens", promptTokens);
      usage.put("completion_tokens", completionTokens);
      usage.put("total_tokens", promptTokens + completionTokens);

      return root.toString();
    }
  }

  /** A tool call requested by the stubbed model. */
  public record ToolCall(String id, String name, String argumentsJson) {
    public static ToolCall of(String id, String name, String argumentsJson) {
      return new ToolCall(id, name, argumentsJson);
    }
  }
}
