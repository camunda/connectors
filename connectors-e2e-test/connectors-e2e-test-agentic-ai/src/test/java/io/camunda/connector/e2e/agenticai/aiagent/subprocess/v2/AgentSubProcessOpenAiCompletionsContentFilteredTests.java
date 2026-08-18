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

import static io.camunda.process.test.api.CamundaAssert.assertThat;

import io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsV2SseChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsV2SseChatModelStubs.ContentFilteredTurnStub;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * OpenAI-Chat-Completions-only e2e coverage proving that a {@code content_filter} finish reason -
 * OpenAI's content filtering rejection - propagates unswallowed through the real {@code
 * OpenAiChatModel} wrapper and {@code BaseAgentRequestHandler} all the way to a BPMN error carrying
 * the {@code rejection} error variables.
 */
class AgentSubProcessOpenAiCompletionsContentFilteredTests
    extends BaseOpenAiCompletionsSubProcessTest {

  @Test
  void contentFilteredRejectionFailsJobAndSurfacesRejectionErrorVariables() throws Exception {
    final var userPrompt = "Write a haiku about the sea";
    final var partialText = "I can help you with a haiku, but";

    OpenAiCompletionsV2SseChatModelStubs.stubConversation(
        new ContentFilteredTurnStub(partialText, 10, 20));

    final var errorExpression =
        """
        =if error.code = "MODEL_RESPONSE_CONTENT_FILTERED" then
          bpmnError(error.code, error.message, {
            errorCode: error.code,
            stopReason: error.variables.rejection.stopReason,
            text: error.variables.rejection.text
          })
        else
          null
        """;

    final var zeebeTest =
        awaitProcessCompletion(
            createProcessInstance(
                elementTemplate -> elementTemplate.property("errorExpression", errorExpression),
                Map.of("userPrompt", userPrompt)));

    // "AI_Agent" itself is deliberately not part of this ordering assertion: this scenario
    // rejects on the very first model call, so the ad-hoc sub-process is TERMINATED (interrupted
    // by the boundary event), not COMPLETED; only the boundary event and the end event it flows
    // into complete normally.
    assertThat(zeebeTest.getProcessInstanceEvent())
        .hasNoActiveIncidents()
        .hasCompletedElementsInOrder("ErrorBoundary_ContentFiltered", "EndEvent_ContentFiltered")
        .hasVariable(
            "rejectionErrorCode", AgentErrorCodes.ERROR_CODE_MODEL_RESPONSE_CONTENT_FILTERED)
        .hasVariable("rejectionStopReason", "content_filter")
        .hasVariable("rejectionText", partialText);
  }
}
