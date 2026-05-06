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
package io.camunda.connector.e2e.agenticai.aiagent.wireformat;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.connector.e2e.agenticai.aiagent.BaseAiAgentJobWorkerTest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;

/**
 * Base class for wire-format E2E tests that verify the AI Agent connector against a real HTTP API
 * contract, mocked via WireMock. Unlike the Mockito-based tests that intercept at the {@code
 * ChatModelFactory} level, these tests drive the full LLM HTTP stack so they remain valid as a
 * regression suite when the provider implementation changes (e.g. switching from LangChain4j to the
 * native provider layer described in ADR 004).
 *
 * <p>Test scenario: single tool call (SuperfluxProduct) followed by a final text response, user
 * satisfied on first feedback.
 */
abstract class BaseWireFormatAiAgentJobWorkerTest extends BaseAiAgentJobWorkerTest {

  protected static final String RESPONSE_TEXT = "The SuperfluxProduct of 5 and 3 is 24.";
  protected static final String USER_PROMPT = "Calculate the superflux product of 5 and 3";

  private static final String SCENARIO_NAME = "LLM Tool Call Flow";
  private static final String AFTER_TOOL_CALL_STATE = "AfterToolCall";

  // Token counts in stub responses: turn1 input=100 output=50, turn2 input=200 output=30
  protected static final AgentMetrics EXPECTED_METRICS =
      new AgentMetrics(2, new AgentMetrics.TokenUsage(300, 80));

  protected int wireMockPort;

  @BeforeEach
  void captureWireMockPort(WireMockRuntimeInfo wireMockRuntimeInfo) {
    wireMockPort = wireMockRuntimeInfo.getHttpPort();
  }

  /**
   * Sets user feedback to "satisfied" before each test so the process completes without a follow-up
   * loop. Runs after {@link
   * io.camunda.connector.e2e.agenticai.aiagent.BaseAiAgentTest#openUserFeedbackJobWorker()} resets
   * the reference to an empty map.
   */
  @BeforeEach
  void setUserFeedbackToSatisfied() {
    userFeedbackVariables.set(userSatisfiedFeedback());
  }

  /** The API path WireMock should intercept, e.g. {@code /v1/messages}. */
  protected abstract String llmApiPath();

  /** Response body for the first LLM call — must instruct the agent to call SuperfluxProduct. */
  protected abstract String toolCallResponseBody();

  /** Response body for the second LLM call — final text answer, no tool calls. */
  protected abstract String finalResponseBody();

  protected void stubLlmApiForToolCallThenFinalResponse() {
    stubFor(
        post(urlEqualTo(llmApiPath()))
            .inScenario(SCENARIO_NAME)
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(toolCallResponseBody()))
            .willSetStateTo(AFTER_TOOL_CALL_STATE));

    stubFor(
        post(urlEqualTo(llmApiPath()))
            .inScenario(SCENARIO_NAME)
            .whenScenarioStateIs(AFTER_TOOL_CALL_STATE)
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(finalResponseBody())));
  }

  protected ZeebeTest runToolCallScenario() throws Exception {
    stubLlmApiForToolCallThenFinalResponse();
    return createProcessInstance(Map.of("userPrompt", USER_PROMPT)).waitForProcessCompletion();
  }
}
