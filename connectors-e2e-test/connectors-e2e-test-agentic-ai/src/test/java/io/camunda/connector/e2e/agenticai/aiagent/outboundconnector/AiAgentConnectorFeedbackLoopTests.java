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
package io.camunda.connector.e2e.agenticai.aiagent.outboundconnector;

import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.HAIKU_TEXT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.OpenAiChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.OpenAiChatModelStubs.Turn;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.RecordedLlmConversation;
import io.camunda.connector.e2e.agenticai.assertj.AgentResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

@SlowTest
public class AiAgentConnectorFeedbackLoopTests extends BaseAiAgentConnectorTest {

  private static final String EMOJI_HAIKU_TEXT =
      "Endless waves whisper 🌊 | moonlight dances on the tide 🌕 | secrets drift below 🌌";

  @Test
  void executesAgentWithoutUserFeedback() throws Exception {
    testBasicExecutionWithoutFeedbackLoop(
        e -> e,
        HAIKU_TEXT,
        true,
        (agentResponse) ->
            AgentResponseAssert.assertThat(agentResponse)
                .hasResponseMessageText(HAIKU_TEXT)
                .hasResponseText(HAIKU_TEXT)
                .hasNoResponseJson());
  }

  @Test
  void basicExecutionWorksWithoutOptionalConfiguration() throws Exception {
    testBasicExecutionWithoutFeedbackLoop(
        elementTemplate -> {
          for (String prefix :
              List.of("data.tools.", "data.memory.", "data.limits.", "data.response.")) {
            elementTemplate = elementTemplate.withoutPropertyValueStartingWith(prefix);
          }
          return elementTemplate;
        },
        HAIKU_TEXT,
        false,
        (agentResponse) ->
            // defaults to text
            AgentResponseAssert.assertThat(agentResponse)
                .hasNoResponseMessage()
                .hasResponseText(HAIKU_TEXT)
                .hasNoResponseJson());

    // verify that tool schema resolver was called with an empty list (no tools configured)
    verify(toolsSchemaResolver).resolveAdHocToolsSchema(List.of());
  }

  @Test
  void executesAgentWithUserFeedback() throws Exception {
    final var initialUserPrompt = "Write a haiku about the sea";

    OpenAiChatModelStubs.stubConversation(
        Turn.text(HAIKU_TEXT, 10, 20), Turn.text(EMOJI_HAIKU_TEXT, 11, 22));

    enqueueUserFeedback(userFollowUpFeedback("Add emojis!"), userSatisfiedFeedback());

    final var zeebeTest =
        createProcessInstance(Map.of("userPrompt", initialUserPrompt)).waitForProcessCompletion();

    final var recorded = RecordedLlmConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(2);

    assertConversationMessages(
        recorded.lastRequest(),
        ExpectedMessage.system(SYSTEM_PROMPT),
        ExpectedMessage.user(initialUserPrompt),
        ExpectedMessage.assistant(HAIKU_TEXT),
        ExpectedMessage.user("Add emojis!"));

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasNoToolCalls()
                .hasMetrics(new AgentMetrics(2, new AgentMetrics.TokenUsage(21, 42), 0))
                .hasResponseMessageText(EMOJI_HAIKU_TEXT)
                .hasResponseText(EMOJI_HAIKU_TEXT));

    assertThat(userFeedbackJobWorkerCounter.get()).isEqualTo(2);
  }
}
