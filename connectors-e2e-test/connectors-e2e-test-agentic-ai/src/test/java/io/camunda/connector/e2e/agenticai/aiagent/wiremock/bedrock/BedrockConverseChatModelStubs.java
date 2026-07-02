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

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.ScenarioMappingBuilder;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stubs the AWS Bedrock Converse endpoint ({@code POST /model/test-model/converse}) so the AI agent
 * connector drives the conversation loop against a mock HTTP endpoint.
 *
 * <p>Mirrors {@code OpenAiCompletionsChatModelStubs}/{@code AnthropicMessagesChatModelStubs}: a
 * conversation is expressed as an ordered list of {@link Turn turns}, each advancing a {@link
 * Scenario} state so sequential calls deterministically receive the next turn's response regardless
 * of request body.
 *
 * <p>Unlike OpenAI/Anthropic, Bedrock's content blocks are discriminated by which key is present
 * (e.g. {@code text}, {@code toolUse}) rather than a {@code type} field — confirmed against the
 * real AWS Bedrock Converse API during calibration.
 */
public final class BedrockConverseChatModelStubs {

  public static final String CONVERSE_PATH = "/model/test-model/converse";

  private static final String SCENARIO_NAME = "llm-conversation";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final AtomicInteger TURN_COUNTER = new AtomicInteger(0);

  private BedrockConverseChatModelStubs() {}

  /** Wires the scenario chain returning each turn's response in order. */
  public static void stubConversation(Turn... turns) {
    final var turnList = Arrays.asList(turns);
    if (turnList.isEmpty()) {
      throw new IllegalArgumentException("At least one conversation turn is required");
    }

    for (int i = 0; i < turnList.size(); i++) {
      final String fromState = i == 0 ? Scenario.STARTED : stateName(i);

      ScenarioMappingBuilder mapping =
          post(urlPathEqualTo(CONVERSE_PATH))
              .inScenario(SCENARIO_NAME)
              .whenScenarioStateIs(fromState)
              .willReturn(createResponse(turns[i]));

      if (i < turnList.size() - 1) {
        mapping = mapping.willSetStateTo(stateName(i + 1));
      }

      stubFor(mapping);
    }
  }

  private static ResponseDefinitionBuilder createResponse(Turn turn) {
    return aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody(turn.toResponseJson());
  }

  private static String stateName(int index) {
    return "turn-" + index;
  }

  /** A single assistant turn in the conversation. */
  public static final class Turn {

    private final String text;
    private final List<ToolCall> toolCalls;
    private final int inputTokens;
    private final int outputTokens;

    private Turn(String text, List<ToolCall> toolCalls, int inputTokens, int outputTokens) {
      this.text = text;
      this.toolCalls = toolCalls;
      this.inputTokens = inputTokens;
      this.outputTokens = outputTokens;
    }

    /** A plain text response that ends the turn ({@code stopReason: "end_turn"}). */
    public static Turn text(String text, int inputTokens, int outputTokens) {
      return new Turn(text, List.of(), inputTokens, outputTokens);
    }

    /**
     * A tool-call response ({@code stopReason: "tool_use"}). The optional assistant text is
     * included alongside the tool calls, matching how Bedrock returns reasoning text with tool
     * calls.
     */
    public static Turn toolCalls(
        String text, int inputTokens, int outputTokens, ToolCall... toolCalls) {
      return new Turn(text, Arrays.asList(toolCalls), inputTokens, outputTokens);
    }

    private String toResponseJson() {
      try {
        return OBJECT_MAPPER.writeValueAsString(buildResponse());
      } catch (JsonProcessingException e) {
        throw new RuntimeException(e);
      }
    }

    private ConverseResponse buildResponse() {
      final List<Object> content = new ArrayList<>();
      if (text != null && !text.isBlank()) {
        content.add(new TextBlock(text));
      }
      toolCalls.forEach(
          tc ->
              content.add(
                  new ToolUseBlockWrapper(
                      new ToolUseBlock(tc.id(), tc.name(), tc.argumentsJson()))));

      return new ConverseResponse(
          new Output(new Message("assistant", content)),
          toolCalls.isEmpty() ? "end_turn" : "tool_use",
          new UsageInfo(inputTokens, outputTokens, inputTokens + outputTokens));
    }

    private record ConverseResponse(Output output, String stopReason, UsageInfo usage) {}

    private record Output(Message message) {}

    private record Message(String role, List<Object> content) {}

    private record TextBlock(String text) {}

    private record ToolUseBlockWrapper(ToolUseBlock toolUse) {}

    private record ToolUseBlock(String toolUseId, String name, @JsonRawValue String input) {}

    private record UsageInfo(int inputTokens, int outputTokens, int totalTokens) {}
  }

  /** A tool call requested by the stubbed model. */
  public record ToolCall(String id, String name, String argumentsJson) {
    public static ToolCall of(String id, String name, String argumentsJson) {
      return new ToolCall(id, name, argumentsJson);
    }
  }
}
