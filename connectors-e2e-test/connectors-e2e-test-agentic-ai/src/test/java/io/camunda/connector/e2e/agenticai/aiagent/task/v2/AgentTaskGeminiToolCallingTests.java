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
package io.camunda.connector.e2e.agenticai.aiagent.task.v2;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.gemini.StreamingGeminiChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.ToolCallStub;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.TurnStub;
import io.camunda.connector.e2e.agenticai.assertj.AgentResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

/**
 * Native-Gemini e2e coverage for the AI Agent <b>Task</b> flavor: the same WireMock-served Gemini
 * traffic as the sub-process tests, but driven through the Task flavor's explicitly-modeled BPMN
 * tool-calling loop, whose provider configuration is re-evaluated per iteration rather than frozen
 * at sub-process entry.
 *
 * <p>The point of this class is not to re-verify Gemini's wire format (the {@code subprocess/v2}
 * tests do that in depth) but to prove the native Gemini provider resolves and drives correctly on
 * the Task flavor too — the v2 Task template's {@code provider.googleGemini.*} properties, the same
 * multi-turn {@code functionCall}/{@code functionResponse} exchange, and the Task-flavor agent
 * response.
 */
@SlowTest
class AgentTaskGeminiToolCallingTests extends BaseGeminiNativeTaskTest {

  private static final String TOOL_NAME = "SuperfluxProduct";

  @Test
  void executesToolCallingLoopAgainstNativeGeminiProvider() throws Exception {
    final var initialUserPrompt = "Explore some of your tools!";
    final var toolCallMessage = "I will call the superflux calculation tool.";
    final var finalResponseText = "The superflux calculation of 5 and 3 is 24.";

    StreamingGeminiChatModelStubs.stubConversation(
        TurnStub.toolCalls(
            toolCallMessage,
            10,
            20,
            new ToolCallStub("unused-on-the-developer-api", TOOL_NAME, "{\"a\": 5, \"b\": 3}")),
        TurnStub.text(finalResponseText, 11, 22));
    enqueueUserFeedback(userSatisfiedFeedback());

    final var zeebeTest =
        awaitProcessCompletion(createProcessInstance(Map.of("userPrompt", initialUserPrompt)));

    final var requests = recordedLoggedRequests(2);

    assertThat(requestedModel(requests.get(0)))
        .as("model id in the request URL")
        .isEqualTo(defaultModel());

    final var firstRequest = parseBody(requests.get(0));
    assertThat(functionDeclarationNames(firstRequest))
        .as("advertised function declarations")
        .contains(TOOL_NAME);
    assertThat(firstRequest.path("systemInstruction").path("parts").path(0).path("text").asText())
        .as("system prompt is hoisted to systemInstruction")
        .isEqualTo(expectedSystemPrompt());

    // The follow-up request replays the model's functionCall turn plus the tool result, which on
    // Gemini is a user-role functionResponse part (there is no dedicated "tool" role).
    final var secondRequest = parseBody(requests.get(1));
    assertThat(contentRoles(secondRequest))
        .as("content roles on the follow-up request")
        .containsExactly("user", "model", "user");

    final var functionCall =
        secondRequest.path("contents").path(1).path("parts").path(1).path("functionCall");
    final var functionResponse =
        secondRequest.path("contents").path(2).path("parts").path(0).path("functionResponse");

    assertThat(functionCall.path("name").asText())
        .as("replayed functionCall.name")
        .isEqualTo(TOOL_NAME);
    assertThat(functionResponse.path("response").path("output").asText())
        .as("tool result payload")
        .isEqualTo("24");
    assertThat(functionResponse.path("id").asText())
        .as("functionResponse.id echoes the functionCall.id, correlating the result to its call")
        .isEqualTo(functionCall.path("id").asText());

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasNoToolCalls()
                .hasMetrics(new AgentMetrics(2, new AgentMetrics.TokenUsage(21, 42), 1))
                .hasResponseMessageText(finalResponseText)
                .hasResponseText(finalResponseText));

    assertThat(userFeedbackJobWorkerCounter.get()).isEqualTo(1);
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
