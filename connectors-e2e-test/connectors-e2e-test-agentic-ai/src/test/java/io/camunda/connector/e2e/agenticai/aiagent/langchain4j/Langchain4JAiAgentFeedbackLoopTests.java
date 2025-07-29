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
package io.camunda.connector.e2e.agenticai.aiagent.langchain4j;

import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.HAIKU_TEXT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.e2e.agenticai.assertj.AgentResponseAssert;
import io.camunda.connector.test.SlowTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

@SlowTest
public class Langchain4JAiAgentFeedbackLoopTests extends BaseLangchain4JAiAgentTests {

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

    verify(toolsSchemaResolver, never()).resolveAdHocToolsSchema(any());
  }

  @Test
  void executesAgentWithUserFeedback() throws Exception {
    final var initialUserPrompt = "Write a haiku about the sea";
    final var expectedConversation =
        List.of(
            new SystemMessage(
                "You are a helpful AI assistant. Answer all the questions, but always be nice. Explain your thinking."),
            new UserMessage(initialUserPrompt),
            new AiMessage(HAIKU_TEXT),
            new UserMessage("Add emojis!"),
            new AiMessage(
                "Endless waves whisper \uD83C\uDF0A | moonlight dances on the tide \uD83C\uDF15 | secrets drift below \uD83C\uDF0C"));

    mockChatInteractions(
        ChatInteraction.of(
            ChatResponse.builder()
                .metadata(
                    ChatResponseMetadata.builder()
                        .finishReason(FinishReason.STOP)
                        .tokenUsage(new TokenUsage(10, 20))
                        .build())
                .aiMessage(new AiMessage(HAIKU_TEXT))
                .build(),
            userFollowUpFeedback("Add emojis!")),
        ChatInteraction.of(
            ChatResponse.builder()
                .metadata(
                    ChatResponseMetadata.builder()
                        .finishReason(FinishReason.STOP)
                        .tokenUsage(new TokenUsage(11, 22))
                        .build())
                .aiMessage(
                    new AiMessage(
                        "Endless waves whisper \uD83C\uDF0A | moonlight dances on the tide \uD83C\uDF15 | secrets drift below \uD83C\uDF0C"))
                .build(),
            userSatisfiedFeedback()));

    final var zeebeTest =
        createProcessInstance(Map.of("action", "executeAgent", "userPrompt", initialUserPrompt))
            .waitForProcessCompletion();

    assertLastChatRequest(2, expectedConversation);

    String expectedResponseText = ((AiMessage) expectedConversation.getLast()).text();
    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasNoToolCalls()
                .hasMetrics(new AgentMetrics(2, new AgentMetrics.TokenUsage(21, 42)))
                .hasResponseMessageText(expectedResponseText)
                .hasResponseText(expectedResponseText));

    assertThat(jobWorkerCounter.get()).isEqualTo(2);
  }
}
