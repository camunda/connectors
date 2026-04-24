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

import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.AI_AGENT_CONNECTOR_ELEMENT_TEMPLATE_PROPERTIES;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.ChatModelFactory;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureOpenAiProviderConfiguration;
import io.camunda.connector.test.utils.annotation.SlowTest;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Regression test for the existing Azure OpenAI provider configuration path.
 *
 * <p>Confirms that pre-existing BPMN process definitions using the {@code "type": "azureOpenAi"}
 * provider type continue to work end-to-end after the Milestone 1 AzureAuthentication extraction
 * and the upcoming Milestone 2 OpenAI builder helper extraction.
 *
 * <p>The real Azure SDK client is not invoked here — instead the {@link ChatModelFactory} is mocked
 * at the Spring bean level. This is required because the Azure SDK's {@code KeyCredentialPolicy}
 * rejects API keys sent over plain HTTP (WireMock uses HTTP). The mock still exercises the full
 * connector stack: element-template deserialization, provider-type dispatch, {@link
 * AzureOpenAiProviderConfiguration} binding, and agent-loop orchestration.
 *
 * <p>Green from day one — if this test fails, a regression has already been introduced.
 */
@SlowTest
@ExtendWith(MockitoExtension.class)
class AzureOpenAiLegacyCompatibilityE2ETest extends BaseAiAgentConnectorTest {

  @MockitoBean private ChatModelFactory chatModelFactory;
  @Mock private ChatModel chatModel;

  @BeforeEach
  void setUpChatModelFactory() {
    when(chatModelFactory.createChatModel(any())).thenReturn(chatModel);
  }

  @Override
  protected Map<String, String> elementTemplateProperties() {
    final var properties = new HashMap<>(AI_AGENT_CONNECTOR_ELEMENT_TEMPLATE_PROPERTIES);
    properties.put("provider.type", "azureOpenAi");
    // The Azure OpenAI endpoint is not actually called — the ChatModelFactory is mocked.
    // A placeholder value is still required so element-template validation passes.
    properties.put("provider.azureOpenAi.endpoint", "https://placeholder.openai.azure.com");
    properties.put("provider.azureOpenAi.authentication.type", "apiKey");
    properties.put("provider.azureOpenAi.authentication.apiKey", "test-api-key");
    properties.put("provider.azureOpenAi.model.deploymentName", "gpt-4o");
    // remove openai provider keys inherited from the fixture so they don't conflict
    properties.remove("provider.openai.authentication.apiKey");
    properties.remove("provider.openai.model.model");
    return properties;
  }

  @Test
  void agentLoopCompletesWithoutToolCalls() throws Exception {
    // Single-turn scenario: the model returns a direct response with no tool calls.
    // This exercises the full connector stack (deserialization → dispatch → model call →
    // agent response) without routing through the BPMN tool-call sub-process.
    when(chatModel.chat(any(ChatRequest.class)))
        .thenReturn(
            ChatResponse.builder()
                .aiMessage(AiMessage.from("It's sunny in Berlin."))
                .metadata(
                    ChatResponseMetadata.builder()
                        .finishReason(FinishReason.STOP)
                        .tokenUsage(new TokenUsage(42, 18))
                        .build())
                .build());

    // Signal user satisfaction so the BPMN feedback loop exits after the first agent response.
    userFeedbackVariables.set(userSatisfiedFeedback());

    final var zeebeTest =
        createProcessInstance(
            elementTemplate -> elementTemplate,
            Map.of("userPrompt", "Write a haiku about the sea"));

    // This test must pass from day one. If the agent loop fails here, a regression has been
    // introduced in the legacy Azure OpenAI dispatch path.
    zeebeTest.waitForProcessCompletion();
  }
}
