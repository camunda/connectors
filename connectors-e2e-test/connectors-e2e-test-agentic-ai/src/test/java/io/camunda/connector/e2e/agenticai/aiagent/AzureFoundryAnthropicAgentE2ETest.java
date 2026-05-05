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

  private static final String MESSAGES_PATH = "/anthropic/v1/messages";
  private static final String SCENARIO_NAME = "foundry-anthropic-tool-call";
  private static final String SCENARIO_STATE_GOT_TOOL_CALL = "got_tool_call";
  private static final String DEPLOYMENT_NAME = "claude-sonnet-4-6";
  private static final String TOOL_USE_ID = "toolu_01";

  @Override
  protected Map<String, String> elementTemplateProperties() {
    final var properties = new HashMap<>(AI_AGENT_CONNECTOR_ELEMENT_TEMPLATE_PROPERTIES);
    properties.put("provider.type", "azureAiFoundry");
    properties.put("provider.azureAiFoundry.authentication.type", "apiKey");
    properties.put("provider.azureAiFoundry.authentication.apiKey", "test-api-key");
    properties.put("provider.azureAiFoundry.model.family", "anthropic");
    properties.put("provider.azureAiFoundry.model.anthropic.deploymentName", DEPLOYMENT_NAME);
    properties.remove("provider.openai.authentication.apiKey");
    properties.remove("provider.openai.model.model");
    return properties;
  }

  @Test
  void agentLoopCompletesWithToolCallRoundTrip(WireMockRuntimeInfo wireMock) throws Exception {
    stubFirstTurnWithToolCallResponse();
    stubSecondTurnWithFinalTextResponse();
    userFeedbackVariables.set(userSatisfiedFeedback());

    final var zeebeTest =
        createProcessInstance(
            elementTemplate ->
                elementTemplate.property(
                    "provider.azureAiFoundry.endpoint", wireMock.getHttpBaseUrl()),
            Map.of("userPrompt", "Write a haiku about the sea"));
    zeebeTest.waitForProcessCompletion();

    List<LoggedRequest> requests = findAll(postRequestedFor(urlEqualTo(MESSAGES_PATH)));
    assertThat(requests).hasSize(2);

    JsonNode turn1 = objectMapper.readTree(requests.get(0).getBodyAsString());
    JsonNode turn2 = objectMapper.readTree(requests.get(1).getBodyAsString());
    assertTurn1RequestBody(turn1);
    assertTurn2RequestBody(turn2);
  }

  private static void stubFirstTurnWithToolCallResponse() {
    stubFor(
        post(urlEqualTo(MESSAGES_PATH))
            .inScenario(SCENARIO_NAME)
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
                          "model": "%s",
                          "content": [
                            {
                              "type": "tool_use",
                              "id": "%s",
                              "name": "GetDateAndTime",
                              "input": {}
                            }
                          ],
                          "stop_reason": "tool_use",
                          "usage": {"input_tokens": 42, "output_tokens": 18}
                        }
                        """
                            .formatted(DEPLOYMENT_NAME, TOOL_USE_ID)))
            .willSetStateTo(SCENARIO_STATE_GOT_TOOL_CALL));
  }

  private static void stubSecondTurnWithFinalTextResponse() {
    stubFor(
        post(urlEqualTo(MESSAGES_PATH))
            .inScenario(SCENARIO_NAME)
            .whenScenarioStateIs(SCENARIO_STATE_GOT_TOOL_CALL)
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
                          "model": "%s",
                          "content": [{"type": "text", "text": "It's sunny in Berlin."}],
                          "stop_reason": "end_turn",
                          "usage": {"input_tokens": 60, "output_tokens": 12}
                        }
                        """
                            .formatted(DEPLOYMENT_NAME))));
  }

  private void assertTurn1RequestBody(JsonNode turn1) {
    assertThat(turn1.path("model").asText()).isEqualTo(DEPLOYMENT_NAME);
    assertThat(messageContentText(turn1, 0)).contains("Write a haiku about the sea");
    assertThat(toolNamesIn(turn1)).contains("GetDateAndTime");
  }

  private void assertTurn2RequestBody(JsonNode turn2) {
    assertThat(turn2.path("messages").size()).isGreaterThanOrEqualTo(3);
    assertThat(containsToolResultBlock(turn2.path("messages"), TOOL_USE_ID)).isTrue();
  }

  private static String messageContentText(JsonNode request, int messageIndex) {
    JsonNode content = request.path("messages").get(messageIndex).path("content");
    return content.isTextual() ? content.asText() : content.toString();
  }

  private static List<String> toolNamesIn(JsonNode request) {
    List<String> names = new ArrayList<>();
    request.path("tools").forEach(tool -> names.add(tool.path("name").asText()));
    return names;
  }

  private static boolean containsToolResultBlock(JsonNode messages, String toolUseId) {
    for (JsonNode msg : messages) {
      if (!"user".equals(msg.path("role").asText())) continue;
      JsonNode content = msg.path("content");
      if (!content.isArray()) continue;
      for (JsonNode block : content) {
        if ("tool_result".equals(block.path("type").asText())
            && toolUseId.equals(block.path("tool_use_id").asText())) {
          return true;
        }
      }
    }
    return false;
  }
}
