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
package io.camunda.connector.e2e.agenticai.aiagent.wiremock.azureopenai;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.ScenarioMappingBuilder;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stubs the Azure OpenAI chat completions endpoint ({@code POST
 * /openai/deployments/test-model/chat/completions}) so the AI agent connector drives the
 * conversation loop against a mock HTTP endpoint.
 *
 * <p>Confirmed (via {@code com.azure:azure-ai-openai:1.0.0-beta.16} source inspection — the {@code
 * ChatCompletionsOptions}/{@code ChatRequestMessage}/{@code ChatResponseMessage} wire model classes
 * used by {@code AzureOpenAiChatModel}) that Azure OpenAI's request/response body shape is
 * byte-for-byte identical to OpenAI's Chat Completions format (same {@code messages}/{@code
 * tool_calls}/{@code response_format} field names) — only the URL path (deployment-based, prefixed
 * with {@code /openai}) and authentication differ. This class therefore mirrors {@code
 * OpenAiCompletionsChatModelStubs} closely by design, not by omission; if a future Azure-specific
 * wire quirk is found, that is exactly the kind of drift this suite exists to catch. No Azure
 * credentials were available to calibrate this against the real API — unlike the Anthropic/Bedrock
 * fixtures, this one is source-derived only.
 */
public final class AzureOpenAiCompletionsChatModelStubs {

  public static final String CHAT_COMPLETIONS_PATH =
      "/openai/deployments/test-model/chat/completions";

  private static final String SCENARIO_NAME = "llm-conversation";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final AtomicInteger TURN_COUNTER = new AtomicInteger(0);

  private AzureOpenAiCompletionsChatModelStubs() {}

  /** Wires the scenario chain returning each turn's response in order. */
  public static void stubConversation(Turn... turns) {
    final var turnList = Arrays.asList(turns);
    if (turnList.isEmpty()) {
      throw new IllegalArgumentException("At least one conversation turn is required");
    }

    for (int i = 0; i < turnList.size(); i++) {
      final String fromState = i == 0 ? Scenario.STARTED : stateName(i);

      ScenarioMappingBuilder mapping =
          post(urlPathEqualTo(CHAT_COMPLETIONS_PATH))
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

    private final Integer id;
    private final String text;
    private final List<ToolCall> toolCalls;
    private final int promptTokens;
    private final int completionTokens;

    private Turn(String text, List<ToolCall> toolCalls, int promptTokens, int completionTokens) {
      this.id = TURN_COUNTER.getAndIncrement();
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
     * included alongside the tool calls, matching how Azure OpenAI returns reasoning text with tool
     * calls.
     */
    public static Turn toolCalls(
        String text, int promptTokens, int completionTokens, ToolCall... toolCalls) {
      return new Turn(text, Arrays.asList(toolCalls), promptTokens, completionTokens);
    }

    private String toResponseJson() {
      try {
        return OBJECT_MAPPER.writeValueAsString(buildResponse());
      } catch (JsonProcessingException e) {
        throw new RuntimeException(e);
      }
    }

    private ChatCompletionResponse buildResponse() {
      final var toolCallResponses =
          toolCalls.isEmpty()
              ? null
              : toolCalls.stream()
                  .map(
                      tc ->
                          new ToolCallResponse(
                              tc.id(), "function", new FunctionCall(tc.name(), tc.argumentsJson())))
                  .toList();
      final var message = new AssistantMessage("assistant", text, toolCallResponses);
      final var choice = new Choice(0, message, toolCalls.isEmpty() ? "stop" : "tool_calls");
      final var usage =
          new UsageInfo(promptTokens, completionTokens, promptTokens + completionTokens);
      return new ChatCompletionResponse(
          "chatcmpl-test-%s".formatted(id),
          "chat.completion",
          1700000000L,
          "test-model",
          List.of(choice),
          usage);
    }

    private record ChatCompletionResponse(
        String id,
        String object,
        long created,
        String model,
        List<Choice> choices,
        UsageInfo usage) {}

    private record Choice(
        int index, AssistantMessage message, @JsonProperty("finish_reason") String finishReason) {}

    private record AssistantMessage(
        String role,
        String content,
        @JsonProperty("tool_calls") @JsonInclude(JsonInclude.Include.NON_NULL)
            List<ToolCallResponse> toolCalls) {}

    private record ToolCallResponse(String id, String type, FunctionCall function) {}

    private record FunctionCall(String name, String arguments) {}

    private record UsageInfo(
        @JsonProperty("prompt_tokens") int promptTokens,
        @JsonProperty("completion_tokens") int completionTokens,
        @JsonProperty("total_tokens") int totalTokens) {}
  }

  /** A tool call requested by the stubbed model. */
  public record ToolCall(String id, String name, String argumentsJson) {
    public static ToolCall of(String id, String name, String argumentsJson) {
      return new ToolCall(id, name, argumentsJson);
    }
  }
}
