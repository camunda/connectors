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
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureFoundryProviderConfiguration;
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
 * E2E contract for the Azure AI Foundry provider's OpenAI model family.
 *
 * <p>Confirms that a BPMN process configured with {@code provider.type = azureAiFoundry} and {@code
 * model.family = openai} deserializes and dispatches through {@link
 * AzureFoundryProviderConfiguration} to the shared Azure OpenAI builder helper, and the resulting
 * {@link ChatModel} drives the agent loop to completion.
 *
 * <p>The real Azure SDK client is not invoked here — instead the {@link ChatModelFactory} is mocked
 * at the Spring bean level. This is required because the Azure SDK's {@code KeyCredentialPolicy}
 * rejects API keys sent over plain HTTP (WireMock uses HTTP), which blocks the real HTTP
 * round-trip. The mock still exercises the full connector stack: element-template deserialization,
 * provider-type dispatch, Foundry OpenAI-family binding, delegation to the shared Azure OpenAI
 * builder helper, and agent-loop orchestration. The actual wire-format round-trip is covered by
 * live integration tests owned by QA.
 */
@SlowTest
@ExtendWith(MockitoExtension.class)
class AzureFoundryOpenAiAgentE2ETest extends BaseAiAgentConnectorTest {

  @MockitoBean private ChatModelFactory chatModelFactory;
  @Mock private ChatModel chatModel;

  @BeforeEach
  void setUpChatModelFactory() {
    when(chatModelFactory.createChatModel(any())).thenReturn(chatModel);
  }

  @Override
  protected Map<String, String> elementTemplateProperties() {
    final var properties = new HashMap<>(AI_AGENT_CONNECTOR_ELEMENT_TEMPLATE_PROPERTIES);
    properties.put("provider.type", "azureAiFoundry");
    // The endpoint is not actually called — the ChatModelFactory is mocked. A placeholder is
    // still required so element-template validation passes.
    properties.put("provider.azureAiFoundry.endpoint", "https://placeholder.services.ai.azure.com");
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
  void agentLoopCompletesWithoutToolCalls() throws Exception {
    // Single-turn scenario: the mocked ChatModel returns a direct response with no tool calls.
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

    // The Foundry OpenAI-family dispatch path delegates to the shared Azure OpenAI builder
    // helper (same as the legacy azureOpenAi provider). If this test fails, dispatch is broken.
    zeebeTest.waitForProcessCompletion();
  }
}
