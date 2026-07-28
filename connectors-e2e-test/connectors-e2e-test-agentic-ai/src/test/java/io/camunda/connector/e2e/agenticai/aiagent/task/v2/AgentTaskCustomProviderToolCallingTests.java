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

import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.SystemMessage;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.request.v2.CustomProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.agenticai.aiagent.TestChatModelFactoryConfiguration;
import io.camunda.connector.e2e.agenticai.aiagent.TestChatModelFactoryConfiguration.TestChatModelFactory;
import io.camunda.connector.e2e.agenticai.assertj.AgentResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * Exercises the AI Agent Task against the v2 element template with the {@code custom} provider
 * configuration, backed by {@link TestChatModelFactory} instead of a real (or WireMock-stubbed) LLM
 * endpoint. Proves that a user-supplied {@code ChatModelFactory} is resolved and driven through a
 * full multi-round tool-calling loop, and that {@code CustomProviderConfiguration#parameters()} is
 * threaded through to it unchanged.
 */
@SlowTest
@Import(TestChatModelFactoryConfiguration.class)
class AgentTaskCustomProviderToolCallingTests extends BaseAgentTaskV2Test {

  @Autowired private TestChatModelFactory chatModelFactory;

  @BeforeEach
  void resetChatModelFactory() {
    chatModelFactory.reset();
  }

  @Override
  protected Function<ElementTemplate, ElementTemplate> providerConfigurer() {
    return this::configureCustomProvider;
  }

  private ElementTemplate configureCustomProvider(ElementTemplate template) {
    return template
        .property("provider.type", "custom")
        .property("provider.providerType", TestChatModelFactory.PROVIDER_TYPE)
        .property("provider.model", TestChatModelFactory.MODEL_ID)
        .property("provider.parameters", "={\"answer\": \"fortytwo\"}");
  }

  @Test
  void executesToolCallingLoopAgainstCustomProvider() throws Exception {
    final var initialUserPrompt = "Explore some of your tools!";
    final var toolCallMessage = "I will call the superflux calculation tool.";
    final var finalResponseText = "The superflux calculation of 5 and 3 is 24.";

    chatModelFactory.enqueue(
        TestChatModelFactory.toolCalls(
            toolCallMessage,
            10,
            20,
            new ToolCall("aaa111", "SuperfluxProduct", Map.of("a", 5, "b", 3))),
        TestChatModelFactory.text(finalResponseText, 11, 22));

    enqueueUserFeedback(userSatisfiedFeedback());

    final var zeebeTest =
        awaitProcessCompletion(createProcessInstance(Map.of("userPrompt", initialUserPrompt)));

    assertThat(chatModelFactory.lastConfiguration())
        .isInstanceOfSatisfying(
            CustomProviderConfiguration.class,
            customProviderConfiguration ->
                assertThat(customProviderConfiguration.parameters())
                    .containsEntry("answer", "fortytwo"));

    final var recordedRequests = chatModelFactory.recordedRequests();
    assertThat(recordedRequests).hasSize(2);

    final var lastMessages = recordedRequests.get(1).snapshot().messages();
    assertThat(lastMessages).hasSize(4);
    assertThat(lastMessages.get(0)).isInstanceOf(SystemMessage.class);
    assertThat(lastMessages.get(1)).isInstanceOf(UserMessage.class);
    assertThat(lastMessages.get(2))
        .isInstanceOfSatisfying(
            AssistantMessage.class,
            assistantMessage -> {
              assertThat(assistantMessage.hasToolCalls()).isTrue();
              assertThat(assistantMessage.toolCalls())
                  .extracting(ToolCall::name)
                  .containsExactly("SuperfluxProduct");
            });
    assertThat(lastMessages.get(3))
        .isInstanceOfSatisfying(
            ToolCallResultMessage.class,
            toolCallResultMessage ->
                assertThat(toolCallResultMessage.results())
                    .extracting("id")
                    .containsExactly("aaa111"));

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
}
