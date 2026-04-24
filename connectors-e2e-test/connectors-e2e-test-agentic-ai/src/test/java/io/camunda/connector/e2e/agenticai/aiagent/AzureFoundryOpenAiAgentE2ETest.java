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
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.AI_AGENT_CONNECTOR_ELEMENT_TEMPLATE_PROPERTIES;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.camunda.connector.test.utils.annotation.SlowTest;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * E2E contract for the Azure AI Foundry provider's OpenAI model family.
 *
 * <p>Verifies the two-turn tool-call round trip through the Azure OpenAI chat-completions wire
 * format against a WireMock server acting as the Azure AI Foundry endpoint.
 */
@SlowTest
class AzureFoundryOpenAiAgentE2ETest extends BaseAiAgentConnectorTest {

  @Override
  protected Map<String, String> elementTemplateProperties() {
    final var properties = new HashMap<>(AI_AGENT_CONNECTOR_ELEMENT_TEMPLATE_PROPERTIES);
    properties.put("provider.type", "azureAiFoundry");
    // endpoint is injected per-test via the elementTemplateModifier
    properties.put("provider.azureAiFoundry.authentication.type", "apiKey");
    properties.put("provider.azureAiFoundry.authentication.apiKey", "test-api-key");
    properties.put("provider.azureAiFoundry.model.family", "openai");
    properties.put("provider.azureAiFoundry.model.openai.deploymentName", "gpt-4o");
    // remove openai provider keys inherited from the fixture so they don't conflict
    properties.remove("provider.openai.authentication.apiKey");
    properties.remove("provider.openai.model.model");
    return properties;
  }

  @Test
  void agentLoopCompletesWithToolCallRoundTrip(WireMockRuntimeInfo wireMock) throws Exception {
    // Stub 1: first LLM call returns a tool_calls message (OpenAI chat-completions wire format).
    // langchain4j-azure-open-ai injects the deployment name into the URL path:
    // {endpoint}/openai/deployments/{deploymentName}/chat/completions?api-version=...
    // The tool name must match an element ID in the Agent_Tools AHSP of the test BPMN.
    stubFor(
        post(urlPathMatching("/openai/deployments/.*/chat/completions"))
            .inScenario("foundry-openai-tool-call")
            .whenScenarioStateIs(STARTED)
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "id": "chatcmpl-01",
                          "object": "chat.completion",
                          "model": "gpt-4o",
                          "choices": [{
                            "index": 0,
                            "message": {
                              "role": "assistant",
                              "content": null,
                              "tool_calls": [{
                                "id": "call_01",
                                "type": "function",
                                "function": {"name": "GetDateAndTime", "arguments": "{}"}
                              }]
                            },
                            "finish_reason": "tool_calls"
                          }],
                          "usage": {"prompt_tokens": 42, "completion_tokens": 18, "total_tokens": 60}
                        }
                        """))
            .willSetStateTo("got_tool_call"));

    // Stub 2: second LLM call (after tool result) returns a final text response.
    stubFor(
        post(urlPathMatching("/openai/deployments/.*/chat/completions"))
            .inScenario("foundry-openai-tool-call")
            .whenScenarioStateIs("got_tool_call")
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "id": "chatcmpl-02",
                          "object": "chat.completion",
                          "model": "gpt-4o",
                          "choices": [{
                            "index": 0,
                            "message": {"role": "assistant", "content": "It's sunny in Berlin."},
                            "finish_reason": "stop"
                          }],
                          "usage": {"prompt_tokens": 60, "completion_tokens": 12, "total_tokens": 72}
                        }
                        """)));

    // Signal user satisfaction so the BPMN feedback loop exits after the final agent response.
    // Without this, the User_Feedback job completes with userSatisfied=null, which loops back to
    // the AI Agent with the stale toolCallResults still in scope.
    userFeedbackVariables.set(userSatisfiedFeedback());

    // The WireMock base URL is the Azure AI Foundry endpoint the Foundry SDK will call.
    // langchain4j-azure-open-ai appends /openai/deployments/{deploymentName}/chat/completions
    // to this base URL internally.
    final var zeebeTest =
        createProcessInstance(
            elementTemplate ->
                elementTemplate.property(
                    "provider.azureAiFoundry.endpoint", wireMock.getHttpBaseUrl()),
            Map.of("userPrompt", "Write a haiku about the sea"));

    // The two-turn tool-call loop should complete: first turn returns GetDateAndTime tool call,
    // second turn (after tool result) returns final text response, process completes.
    zeebeTest.waitForProcessCompletion();
  }
}
