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
package io.camunda.connector.e2e.agenticai.aiagent.langchain4j.jobworker;

import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.AI_AGENT_TASK_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.HAIKU_TEXT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationContext;
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.agenticai.model.message.SystemMessage;
import io.camunda.connector.agenticai.model.message.UserMessage;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.test.SlowTest;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

@SlowTest
public class L4JAiAgentJobWorkerLimitsTests extends BaseL4JAiAgentJobWorkerTest {

  @Test
  void raisesIncidentWhenMaximumModelCallsAreReached() throws Throwable {
    testMaxModelCallsLoop(
        elementTemplate -> elementTemplate.property("data.limits.maxModelCalls", "9"), 9);
  }

  @Test
  void fallsBackToADefaultMaxModelCallsLimitWhenNotExplicitelyConfigured() throws Throwable {
    // 10 = default value defined in template
    testMaxModelCallsLoop(e -> e, 10);
  }

  @Test
  void fallsBackToADefaultMaxModelCallsLimitWhenMissingFromConfiguration() throws Throwable {
    // 10 = hardcoded default value in agent logic
    testMaxModelCallsLoop(
        elementTemplate -> elementTemplate.withoutPropertyValue("data.limits.maxModelCalls"), 10);
  }

  private void testMaxModelCallsLoop(
      Function<ElementTemplate, ElementTemplate> elementTemplateModifier, int expectedMaxModelCalls)
      throws Throwable {
    // infinite loop - always returning the same answer and handling the same user feedback
    doAnswer(
            invocationOnMock -> {
              userFeedbackVariables.set(userFollowUpFeedback("I don't like it"));
              return ChatResponse.builder()
                  .metadata(
                      ChatResponseMetadata.builder()
                          .finishReason(FinishReason.STOP)
                          .tokenUsage(new TokenUsage(10, 20))
                          .build())
                  .aiMessage(new AiMessage(HAIKU_TEXT))
                  .build();
            })
        .when(chatModel)
        .chat(chatRequestCaptor.capture());

    final var zeebeTest =
        createProcessInstance(
                elementTemplateModifier,
                Map.of("action", "executeAgent", "userPrompt", "Write a haiku about the sea"))
            .waitForActiveIncidents();

    assertIncident(
        zeebeTest,
        incident -> {
          assertThat(incident.getElementId()).isEqualTo(AI_AGENT_TASK_ID);
          assertThat(incident.getErrorMessage())
              .isEqualTo(
                  "Maximum number of model calls reached (modelCalls: %1$d, limit: %1$d)"
                      .formatted(expectedMaxModelCalls));
        });

    assertAgentResponse(
        zeebeTest,
        agentResponse -> {
          assertThat(agentResponse.context().metrics().modelCalls())
              .isEqualTo(expectedMaxModelCalls);

          final var conversationMessages =
              ((InProcessConversationContext) agentResponse.context().conversation()).messages();
          assertThat(conversationMessages)
              .filteredOn(msg -> msg instanceof SystemMessage)
              .hasSize(1);
          assertThat(conversationMessages)
              .filteredOn(msg -> msg instanceof AssistantMessage)
              .hasSize(expectedMaxModelCalls);
          assertThat(conversationMessages)
              .filteredOn(msg -> msg instanceof UserMessage)
              .hasSize(expectedMaxModelCalls);
        });
  }
}
