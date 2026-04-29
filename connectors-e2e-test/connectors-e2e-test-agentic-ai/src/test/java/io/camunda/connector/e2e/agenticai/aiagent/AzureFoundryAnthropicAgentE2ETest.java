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
package io.camunda.connector.e2e.agenticai.aiagent;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.AI_AGENT_CONNECTOR_ELEMENT_TEMPLATE_PROPERTIES;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.connector.test.utils.annotation.SlowTest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * E2E contract for the Azure AI Foundry provider's Anthropic model family.
 *
 * <p>Verifies the two-turn tool-call round trip through the Anthropic Messages wire format against
 * a WireMock server acting as the Azure AI Foundry endpoint.
 */
@SlowTest
class AzureFoundryAnthropicAgentE2ETest extends BaseAiAgentConnectorTest {

  @Override
  protected Map<String, String> elementTemplateProperties() {
    final var properties = new HashMap<>(AI_AGENT_CONNECTOR_ELEMENT_TEMPLATE_PROPERTIES);
    properties.put("provider.type", "azureAiFoundry");
    // endpoint is injected per-test via the elementTemplateModifier
    properties.put("provider.azureAiFoundry.authentication.type", "apiKey");
    properties.put("provider.azureAiFoundry.authentication.apiKey", "test-api-key");
    properties.put("provider.azureAiFoundry.model.family", "anthropic");
    properties.put("provider.azureAiFoundry.model.anthropic.deploymentName", "claude-sonnet-4-6");
    // remove openai provider keys inherited from the fixture so they don't conflict
    properties.remove("provider.openai.authentication.apiKey");
    properties.remove("provider.openai.model.model");
    return properties;
  }

  @Test
  void agentLoopCompletesWithToolCallRoundTrip(WireMockRuntimeInfo wireMock) throws Exception {
    // Stub 1: first LLM call returns a tool_use block (Anthropic Messages wire format).
    // The Foundry SDK appends /anthropic/v1/messages to the base endpoint URL.
    // The tool name must match an element ID in the Agent_Tools AHSP of the test BPMN.
    stubFor(
        post(urlEqualTo("/anthropic/v1/messages"))
            .inScenario("foundry-anthropic-tool-call")
            .whenScenarioStateIs(STARTED)
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "id": "msg_01",
                          "type": "message",
                          "role": "assistant",
                          "model": "claude-sonnet-4-6",
                          "content": [
                            {
                              "type": "tool_use",
                              "id": "toolu_01",
                              "name": "GetDateAndTime",
                              "input": {}
                            }
                          ],
                          "stop_reason": "tool_use",
                          "usage": {"input_tokens": 42, "output_tokens": 18}
                        }
                        """))
            .willSetStateTo("got_tool_call"));

    // Stub 2: second LLM call (after tool result) returns a final text response.
    stubFor(
        post(urlEqualTo("/anthropic/v1/messages"))
            .inScenario("foundry-anthropic-tool-call")
            .whenScenarioStateIs("got_tool_call")
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "id": "msg_02",
                          "type": "message",
                          "role": "assistant",
                          "model": "claude-sonnet-4-6",
                          "content": [{"type": "text", "text": "It's sunny in Berlin."}],
                          "stop_reason": "end_turn",
                          "usage": {"input_tokens": 60, "output_tokens": 12}
                        }
                        """)));

    // Signal user satisfaction so the BPMN feedback loop exits after the final agent response.
    // Without this, the User_Feedback job completes with userSatisfied=null, which loops back to
    // the AI Agent with the stale toolCallResults still in scope.
    userFeedbackVariables.set(userSatisfiedFeedback());

    // The WireMock base URL is the Azure AI Foundry endpoint the Foundry SDK will call.
    // The Foundry SDK appends /anthropic/v1/messages to this base URL internally.
    final var zeebeTest =
        createProcessInstance(
            elementTemplate ->
                elementTemplate.property(
                    "provider.azureAiFoundry.endpoint", wireMock.getHttpBaseUrl()),
            Map.of("userPrompt", "Write a haiku about the sea"));

    // The two-turn tool-call loop should complete: first turn returns GetDateAndTime tool call,
    // second turn (after tool result) returns final text response, process completes.
    zeebeTest.waitForProcessCompletion();

    // --- Request-body contract assertions ---
    // Retrieve all requests the Foundry SDK made to the WireMock Anthropic endpoint.
    // Exactly two requests must have been made: turn 1 (tool call) and turn 2 (final response).
    List<LoggedRequest> requests = findAll(postRequestedFor(urlEqualTo("/anthropic/v1/messages")));
    assertThat(requests)
        .as("Expected exactly 2 requests to /anthropic/v1/messages (turn 1 + turn 2)")
        .hasSize(2);

    JsonNode turn1 = objectMapper.readTree(requests.get(0).getBodyAsString());
    JsonNode turn2 = objectMapper.readTree(requests.get(1).getBodyAsString());

    // Turn 1: model field, user prompt, and tool definitions must be present.
    assertThat(turn1.path("model").asText())
        .as("Turn 1 model must match the configured deployment name")
        .isEqualTo("claude-sonnet-4-6");

    // The first message must be a user message containing the user prompt.
    JsonNode turn1FirstMessage = turn1.path("messages").get(0);
    assertThat(turn1FirstMessage.path("role").asText())
        .as("Turn 1 first message role must be 'user'")
        .isEqualTo("user");
    // Content can be a string or an array of content blocks; check both shapes.
    JsonNode turn1Content = turn1FirstMessage.path("content");
    String turn1ContentText =
        turn1Content.isTextual()
            ? turn1Content.asText()
            : turn1Content.toString(); // array serialized as JSON for contains-check
    assertThat(turn1ContentText)
        .as("Turn 1 first message must contain the user prompt")
        .contains("Write a haiku about the sea");

    // The tools array must be present and include the BPMN-defined GetDateAndTime tool.
    JsonNode tools = turn1.path("tools");
    assertThat(tools.isArray()).as("Turn 1 must include a 'tools' array").isTrue();
    List<String> toolNames = new ArrayList<>();
    tools.forEach(tool -> toolNames.add(tool.path("name").asText()));
    assertThat(toolNames)
        .as("Turn 1 tools array must contain 'GetDateAndTime'")
        .contains("GetDateAndTime");

    // Turn 2: message history must include the original user prompt, the assistant tool_use
    // block, and a user-role tool_result block whose tool_use_id matches the stub's "toolu_01".
    JsonNode turn2Messages = turn2.path("messages");
    assertThat(turn2Messages.size())
        .as("Turn 2 must have at least 3 messages (user prompt + assistant tool_use + tool_result)")
        .isGreaterThanOrEqualTo(3);

    boolean foundToolResult = false;
    for (JsonNode msg : turn2Messages) {
      if (!"user".equals(msg.path("role").asText())) continue;
      JsonNode content = msg.path("content");
      if (!content.isArray()) continue;
      for (JsonNode block : content) {
        if ("tool_result".equals(block.path("type").asText())
            && "toolu_01".equals(block.path("tool_use_id").asText())) {
          foundToolResult = true;
          break;
        }
      }
      if (foundToolResult) break;
    }
    assertThat(foundToolResult)
        .as(
            "Turn 2 must contain a user-role message with a tool_result block"
                + " whose tool_use_id == 'toolu_01'")
        .isTrue();
  }
}
