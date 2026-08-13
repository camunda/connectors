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
package io.camunda.connector.e2e.agenticai.aiagent.subprocess.v2;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.gemini.GeminiResponseChunks;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.gemini.StreamingGeminiChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.gemini.StreamingGeminiChatModelStubs.GeminiTurnStub;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.ToolCallStub;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.TurnStub;
import io.camunda.connector.e2e.agenticai.assertj.AgentSubProcessResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

/**
 * Native-Gemini e2e coverage for the full tool-calling round trip: {@code functionCall} out, {@code
 * functionResponse} back in, final answer out — driven through the real vendor SDK over real
 * (WireMock-served) HTTP, asserting Gemini's own request shape at each hop.
 *
 * <p>Two id shapes are covered deliberately, because the two real Gemini surfaces differ and the
 * connector has to work on both:
 *
 * <ul>
 *   <li>The <b>Gemini Developer API</b> leaves {@code FunctionCall.id} unset, so {@code
 *       GeminiContentResponseConverter} synthesizes a UUID. A test on this shape therefore cannot
 *       assert a hard-coded id the way the Anthropic and custom-provider tests assert {@code
 *       "aaa111"}; it asserts the tool name and arguments plus the fact that whatever id was
 *       synthesized is echoed <em>identically</em> onto the replayed {@code functionCall} and its
 *       matching {@code functionResponse}. That correlation is the property the agent loop depends
 *       on.
 *   <li><b>Vertex AI</b> populates the id, so a stub carrying one proves the id-echo path passes a
 *       server-provided value straight through.
 * </ul>
 */
@SlowTest
class AgentSubProcessGeminiToolCallingTests extends BaseGeminiNativeSubProcessTest {

  private static final String TOOL_NAME = "SuperfluxProduct";
  private static final String TOOL_ARGUMENTS_JSON = "{\"a\": 5, \"b\": 3}";
  private static final String TOOL_RESULT = "24";

  @Test
  void executesToolCallingRoundTripWithSynthesizedFunctionCallId() throws Exception {
    final var userPrompt = "Explore some of your tools!";
    final var toolCallMessage = "I will call the superflux calculation tool.";
    final var finalResponseText = "The superflux calculation of 5 and 3 is 24.";

    // Developer-API shape: no FunctionCall.id on the wire (stubConversation omits it).
    StreamingGeminiChatModelStubs.stubConversation(
        TurnStub.toolCalls(
            toolCallMessage,
            10,
            20,
            new ToolCallStub("ignored-by-the-developer-api", TOOL_NAME, TOOL_ARGUMENTS_JSON)),
        TurnStub.text(finalResponseText, 11, 22));
    enqueueUserFeedback(userSatisfiedFeedback());

    final var zeebeTest =
        awaitProcessCompletion(createProcessInstance(Map.of("userPrompt", userPrompt)));

    final var requests = recordedLoggedRequests(2);

    // ---- hop 1: the initial request advertises the tools and carries the user prompt ----
    final var firstRequest = parseBody(requests.get(0));
    assertThat(requestedModel(requests.get(0)))
        .as("model id in the request URL")
        .isEqualTo(defaultModel());
    assertThat(functionDeclarationNames(firstRequest))
        .as("advertised function declarations")
        .contains(TOOL_NAME, "Search_The_Web");
    assertThat(firstRequest.path("systemInstruction").path("parts").path(0).path("text").asText())
        .as("system prompt is hoisted to systemInstruction, not sent as a content role")
        .isEqualTo(expectedSystemPrompt());
    assertThat(contentRoles(firstRequest))
        .as("content roles on the first request")
        .containsExactly("user");

    // ---- hop 2: the follow-up replays the model turn plus the tool result ----
    final var secondRequest = parseBody(requests.get(1));
    // Gemini has no dedicated "tool" role: results come back as a user-role functionResponse part.
    assertThat(contentRoles(secondRequest))
        .as("content roles on the follow-up request")
        .containsExactly("user", "model", "user");

    final var modelParts = secondRequest.path("contents").path(1).path("parts");
    assertThat(modelParts.path(0).path("text").asText())
        .as("replayed assistant text")
        .isEqualTo(toolCallMessage);

    final var functionCall = modelParts.path(1).path("functionCall");
    assertThat(functionCall.path("name").asText())
        .as("replayed functionCall.name")
        .isEqualTo(TOOL_NAME);
    assertThat(functionCall.path("args").path("a").asInt()).as("replayed arg a").isEqualTo(5);
    assertThat(functionCall.path("args").path("b").asInt()).as("replayed arg b").isEqualTo(3);

    final var functionResponse =
        secondRequest.path("contents").path(2).path("parts").path(0).path("functionResponse");
    assertThat(functionResponse.path("name").asText())
        .as("functionResponse.name correlates by tool name")
        .isEqualTo(TOOL_NAME);
    assertThat(functionResponse.path("response").path("output").asText())
        .as("tool result payload")
        .isEqualTo(TOOL_RESULT);

    // The synthesized id is not predictable, but it MUST be the same on both sides - that identity
    // is what lets the agent loop correlate a result back to its originating call.
    final var synthesizedId = functionCall.path("id").asText();
    assertThat(synthesizedId).as("synthesized functionCall.id").isNotBlank();
    assertThat(functionResponse.path("id").asText())
        .as("functionResponse.id must echo the functionCall.id verbatim")
        .isEqualTo(synthesizedId);

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentSubProcessResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasResponseMessageText(finalResponseText)
                .hasResponseText(finalResponseText)
                .hasMetrics(new AgentMetrics(2, new AgentMetrics.TokenUsage(21, 42), 1)));

    assertThat(userFeedbackJobWorkerCounter.get()).isEqualTo(1);
  }

  @Test
  void echoesServerProvidedFunctionCallIdOnTheFollowUpRequest() throws Exception {
    final var userPrompt = "Explore some of your tools!";
    final var serverProvidedId = "gemini-call-0001";
    final var finalResponseText = "The superflux calculation of 5 and 3 is 24.";

    // Vertex-AI shape: the server supplies the functionCall id, so it must survive the round trip
    // untouched rather than being replaced by a synthesized UUID.
    StreamingGeminiChatModelStubs.stubTurns(
        StreamingGeminiChatModelStubs.toolCallTurnWithIds(
            "Calling the tool.",
            10,
            20,
            new ToolCallStub(serverProvidedId, TOOL_NAME, TOOL_ARGUMENTS_JSON)),
        GeminiTurnStub.of(GeminiResponseChunks.text(finalResponseText, 11, 22)));
    enqueueUserFeedback(userSatisfiedFeedback());

    awaitProcessCompletion(createProcessInstance(Map.of("userPrompt", userPrompt)));

    final var secondRequest = parseBody(recordedLoggedRequests(2).get(1));
    final var functionCall =
        secondRequest.path("contents").path(1).path("parts").path(1).path("functionCall");
    final var functionResponse =
        secondRequest.path("contents").path(2).path("parts").path(0).path("functionResponse");

    assertThat(functionCall.path("id").asText())
        .as("server-provided functionCall.id is replayed unchanged")
        .isEqualTo(serverProvidedId);
    assertThat(functionResponse.path("id").asText())
        .as("functionResponse.id matches the server-provided id")
        .isEqualTo(serverProvidedId);
  }

  @Test
  void replaysThoughtSignatureOnTheFunctionCallPartOfTheFollowUpRequest() throws Exception {
    final var userPrompt = "Use the superflux tool on 5 and 3, thinking it through first.";
    final var signature = GeminiResponseChunks.encodeSignature("sig-e2e-gemini-toolcall");
    final var finalResponseText = "The superflux calculation of 5 and 3 is 24.";

    // Gemini 3 stamps a thoughtSignature onto a functionCall it reasoned about and REJECTS a
    // follow-up request whose replayed history dropped it, so this round trip is load-bearing.
    StreamingGeminiChatModelStubs.stubTurns(
        GeminiTurnStub.of(
            GeminiResponseChunks.chunk().thought("Superflux needs both operands.").build(),
            GeminiResponseChunks.chunk()
                .functionCall(
                    new ToolCallStub("unused", TOOL_NAME, TOOL_ARGUMENTS_JSON), false, signature)
                .usage(10, 20, 5, 0)
                .build()),
        GeminiTurnStub.of(GeminiResponseChunks.text(finalResponseText, 11, 22)));
    enqueueUserFeedback(userSatisfiedFeedback());

    awaitProcessCompletion(createProcessInstance(Map.of("userPrompt", userPrompt)));

    final var secondRequest = parseBody(recordedLoggedRequests(2).get(1));
    final var modelParts = secondRequest.path("contents").path(1).path("parts");

    final var functionCallPart =
        StreamSupport.stream(modelParts.spliterator(), false)
            .filter(part -> part.has("functionCall"))
            .findFirst()
            .orElseThrow(
                () -> new AssertionError("No functionCall part replayed on the follow-up request"));

    assertThat(functionCallPart.path("thoughtSignature").asText())
        .as("thoughtSignature replayed verbatim (base64) alongside the functionCall")
        .isEqualTo(signature);
  }

  private List<String> functionDeclarationNames(JsonNode request) {
    return StreamSupport.stream(
            request.path("tools").path(0).path("functionDeclarations").spliterator(), false)
        .map(declaration -> declaration.path("name").asText())
        .toList();
  }

  private List<String> contentRoles(JsonNode request) {
    return StreamSupport.stream(request.path("contents").spliterator(), false)
        .map(content -> content.path("role").asText())
        .toList();
  }
}
