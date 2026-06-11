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
package io.camunda.connector.e2e.agenticai.aiagent.jobworker;

import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.AI_AGENT_TASK_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.HAIKU_TEXT;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.search.enums.IncidentErrorType;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.Turn;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsRecordedConversation;
import io.camunda.connector.e2e.agenticai.assertj.JobWorkerAgentResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

@SlowTest
public class AiAgentJobWorkerFeedbackLoopTests extends BaseAiAgentJobWorkerTest {

  @Test
  void executesAgentWithoutUserFeedback() throws Exception {
    testBasicExecutionWithoutFeedbackLoop(
        e -> e,
        HAIKU_TEXT,
        true,
        (agentResponse) ->
            JobWorkerAgentResponseAssert.assertThat(agentResponse)
                .hasResponseMessageText(HAIKU_TEXT)
                .hasResponseText(HAIKU_TEXT)
                .hasNoResponseJson());
  }

  @Test
  void basicExecutionWorksWithoutOptionalConfiguration() throws Exception {
    testBasicExecutionWithoutFeedbackLoop(
        elementTemplate -> {
          for (String prefix :
              List.of(
                  "data.memory.",
                  "data.limits.",
                  "data.response.includeAssistantMessage",
                  "data.response.format.")) {
            elementTemplate = elementTemplate.withoutPropertyValueStartingWith(prefix);
          }
          return elementTemplate;
        },
        HAIKU_TEXT,
        false,
        (agentResponse) ->
            // defaults to text
            JobWorkerAgentResponseAssert.assertThat(agentResponse)
                .hasNoResponseMessage()
                .hasResponseText(HAIKU_TEXT)
                .hasNoResponseJson());
  }

  @Test
  void executesAgentWithUserFeedback() throws Exception {
    final var initialUserPrompt = "Write a haiku about the sea";
    final var emojiResponse =
        "Endless waves whisper 🌊 | moonlight dances on the tide 🌕 | secrets drift below 🌌";

    OpenAiCompletionsChatModelStubs.stubConversation(
        Turn.text(HAIKU_TEXT, 10, 20), Turn.text(emojiResponse, 11, 22));

    enqueueUserFeedback(userFollowUpFeedback("Add emojis!"), userSatisfiedFeedback());

    final var zeebeTest =
        createProcessInstance(Map.of("userPrompt", initialUserPrompt))
            .waitForProcessCompletion(Duration.ofSeconds(30));

    assertConversationMessages(
        OpenAiCompletionsRecordedConversation.recorded().lastRequest(),
        ExpectedMessage.system(SYSTEM_PROMPT),
        ExpectedMessage.user(initialUserPrompt),
        ExpectedMessage.assistant(HAIKU_TEXT),
        ExpectedMessage.user("Add emojis!"));

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            JobWorkerAgentResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasMetrics(new AgentMetrics(2, new AgentMetrics.TokenUsage(21, 42), 0))
                .hasResponseMessageText(emojiResponse)
                .hasResponseText(emojiResponse));

    assertThat(userFeedbackJobWorkerCounter.get()).isEqualTo(2);
  }

  @Test
  void raisesIncidentWhenUserPromptIsEmpty() throws Exception {
    final var zeebeTest = createProcessInstance(Map.of("userPrompt", "")).waitForActiveIncidents();

    assertIncident(
        zeebeTest,
        incident -> {
          assertThat(incident.getElementId()).isEqualTo(AI_AGENT_TASK_ID);
          assertThat(incident.getErrorType())
              .isEqualTo(IncidentErrorType.AD_HOC_SUB_PROCESS_NO_RETRIES);
          assertThat(incident.getErrorMessage())
              .contains(
                  "Property: data.userPrompt.prompt: Validation failed. Original message: must not be blank");
        });
  }

  @Test
  void mapsIncidentToJobError() throws Exception {
    final var zeebeTest =
        createProcessInstance(
                elementTemplate ->
                    elementTemplate.property(
                        "errorExpression", "=jobError(\"Job error: \" + error.message)"),
                Map.of("userPrompt", ""))
            .waitForActiveIncidents();

    assertIncident(
        zeebeTest,
        incident -> {
          assertThat(incident.getElementId()).isEqualTo(AI_AGENT_TASK_ID);
          assertThat(incident.getErrorType())
              .isEqualTo(IncidentErrorType.AD_HOC_SUB_PROCESS_NO_RETRIES);
          assertThat(incident.getErrorMessage())
              .startsWith("Job error: ")
              .contains(
                  "Property: data.userPrompt.prompt: Validation failed. Original message: must not be blank");
        });
  }
}
