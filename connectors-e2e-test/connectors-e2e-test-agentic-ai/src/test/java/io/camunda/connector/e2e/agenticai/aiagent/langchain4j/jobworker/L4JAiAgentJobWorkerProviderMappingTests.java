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

import static io.camunda.connector.e2e.agenticai.CustomChatModelProviderTestConfiguration.TEST_CUSTOM_PROVIDER_TYPE;
import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.HAIKU_TEXT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.AnthropicChatModelProvider;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.AzureOpenAiChatModelProvider;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.BedrockChatModelProvider;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.ChatModelProvider;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.GoogleVertexAiChatModelProvider;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.OpenAiChatModelProvider;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.OpenAiCompatibleChatModelProvider;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureOpenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.BedrockProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.CustomProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.GoogleVertexAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiCompatibleProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.ProviderConfiguration;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.agenticai.CustomChatModelProviderTestConfiguration;
import io.camunda.connector.e2e.agenticai.aiagent.BaseAiAgentJobWorkerTest;
import io.camunda.connector.test.utils.annotation.SlowTest;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SlowTest
@Import(CustomChatModelProviderTestConfiguration.class)
public class L4JAiAgentJobWorkerProviderMappingTests extends BaseAiAgentJobWorkerTest {

  private static final ChatResponse CHAT_RESPONSE =
      ChatResponse.builder()
          .metadata(
              ChatResponseMetadata.builder()
                  .finishReason(FinishReason.STOP)
                  .tokenUsage(new TokenUsage(10, 20))
                  .build())
          .aiMessage(new AiMessage(HAIKU_TEXT))
          .build();

  @MockitoSpyBean private AnthropicChatModelProvider anthropicChatModelProvider;
  @MockitoSpyBean private BedrockChatModelProvider bedrockChatModelProvider;
  @MockitoSpyBean private AzureOpenAiChatModelProvider azureOpenAiChatModelProvider;
  @MockitoSpyBean private GoogleVertexAiChatModelProvider googleVertexAiChatModelProvider;
  @MockitoSpyBean private OpenAiChatModelProvider openAiChatModelProvider;
  @MockitoSpyBean private OpenAiCompatibleChatModelProvider openAiCompatibleChatModelProvider;

  @Autowired
  @Qualifier("testCustomChatModelProvider")
  private ChatModelProvider testCustomChatModelProvider;

  private ChatModel chatModel;
  private Map<String, ChatModelProvider> providersByType;

  @BeforeEach
  void stubAllProviders() {
    chatModel = mock(ChatModel.class);
    doAnswer(
            invocation -> {
              userFeedbackVariables.set(userSatisfiedFeedback());
              return CHAT_RESPONSE;
            })
        .when(chatModel)
        .chat(any(ChatRequest.class));

    providersByType =
        Map.of(
            AnthropicProviderConfiguration.ANTHROPIC_ID,
            anthropicChatModelProvider,
            BedrockProviderConfiguration.BEDROCK_ID,
            bedrockChatModelProvider,
            AzureOpenAiProviderConfiguration.AZURE_OPENAI_ID,
            azureOpenAiChatModelProvider,
            GoogleVertexAiProviderConfiguration.GOOGLE_VERTEX_AI_ID,
            googleVertexAiChatModelProvider,
            OpenAiProviderConfiguration.OPENAI_ID,
            openAiChatModelProvider,
            OpenAiCompatibleProviderConfiguration.OPENAI_COMPATIBLE_ID,
            openAiCompatibleChatModelProvider,
            TEST_CUSTOM_PROVIDER_TYPE,
            testCustomChatModelProvider);

    // stub createChatModel on every provider so no real SDK clients are created
    providersByType
        .values()
        .forEach(provider -> doReturn(chatModel).when(provider).createChatModel(any()));
  }

  @ParameterizedTest(name = "provider.type={0} resolves to expected provider")
  @MethodSource("providerMappingCases")
  void registryResolvesProviderForProviderType(
      String providerTypeId,
      Function<ElementTemplate, ElementTemplate> templateOverrides,
      Class<? extends ProviderConfiguration> expectedConfigClass)
      throws Exception {
    createProcessInstance(
            template ->
                templateOverrides.apply(
                    template.withoutPropertyValueStartingWith("provider.openai.")),
            Map.of("userPrompt", "Write a haiku about the sea"))
        .waitForProcessCompletion();

    final var expectedProvider = providersByType.get(providerTypeId);
    final var configCaptor = ArgumentCaptor.forClass(ProviderConfiguration.class);
    verify(expectedProvider).createChatModel(configCaptor.capture());

    assertThat(configCaptor.getValue()).isInstanceOf(expectedConfigClass);
    assertThat(configCaptor.getValue().providerId()).isEqualTo(providerTypeId);

    providersByType.entrySet().stream()
        .filter(entry -> !entry.getKey().equals(providerTypeId))
        .forEach(entry -> verify(entry.getValue(), never()).createChatModel(any()));
  }

  static List<Arguments> providerMappingCases() {
    return List.of(
        Arguments.of(
            AnthropicProviderConfiguration.ANTHROPIC_ID,
            (Function<ElementTemplate, ElementTemplate>)
                template ->
                    template
                        .property("provider.type", AnthropicProviderConfiguration.ANTHROPIC_ID)
                        .property("provider.anthropic.authentication.apiKey", "dummy-api-key")
                        .property("provider.anthropic.model.model", "claude-sonnet-4-6"),
            AnthropicProviderConfiguration.class),
        Arguments.of(
            BedrockProviderConfiguration.BEDROCK_ID,
            (Function<ElementTemplate, ElementTemplate>)
                template ->
                    template
                        .property("provider.type", BedrockProviderConfiguration.BEDROCK_ID)
                        .property("provider.bedrock.region", "us-east-1")
                        .property("provider.bedrock.authentication.type", "credentials")
                        .property("provider.bedrock.authentication.accessKey", "dummy-access-key")
                        .property("provider.bedrock.authentication.secretKey", "dummy-secret-key")
                        .property(
                            "provider.bedrock.model.model", "global.anthropic.claude-sonnet-4-6"),
            BedrockProviderConfiguration.class),
        Arguments.of(
            AzureOpenAiProviderConfiguration.AZURE_OPENAI_ID,
            (Function<ElementTemplate, ElementTemplate>)
                template ->
                    template
                        .property("provider.type", AzureOpenAiProviderConfiguration.AZURE_OPENAI_ID)
                        .property(
                            "provider.azureOpenAi.endpoint", "https://dummy.openai.azure.com/")
                        .property("provider.azureOpenAi.authentication.type", "apiKey")
                        .property("provider.azureOpenAi.authentication.apiKey", "dummy-api-key")
                        .property("provider.azureOpenAi.model.deploymentName", "dummy-deployment"),
            AzureOpenAiProviderConfiguration.class),
        Arguments.of(
            GoogleVertexAiProviderConfiguration.GOOGLE_VERTEX_AI_ID,
            (Function<ElementTemplate, ElementTemplate>)
                template ->
                    template
                        .property(
                            "provider.type",
                            GoogleVertexAiProviderConfiguration.GOOGLE_VERTEX_AI_ID)
                        .property("provider.googleVertexAi.projectId", "dummy-project")
                        .property("provider.googleVertexAi.region", "us-central1")
                        .property(
                            "provider.googleVertexAi.authentication.type",
                            "serviceAccountCredentials")
                        .property(
                            "provider.googleVertexAi.authentication.jsonKey",
                            "{\"type\":\"service_account\"}")
                        .property("provider.googleVertexAi.model.model", "gemini-2.0-flash"),
            GoogleVertexAiProviderConfiguration.class),
        Arguments.of(
            OpenAiProviderConfiguration.OPENAI_ID,
            (Function<ElementTemplate, ElementTemplate>)
                template ->
                    template
                        .property("provider.type", OpenAiProviderConfiguration.OPENAI_ID)
                        .property("provider.openai.authentication.apiKey", "dummy-api-key")
                        .property("provider.openai.model.model", "gpt-4o"),
            OpenAiProviderConfiguration.class),
        Arguments.of(
            OpenAiCompatibleProviderConfiguration.OPENAI_COMPATIBLE_ID,
            (Function<ElementTemplate, ElementTemplate>)
                template ->
                    template
                        .property(
                            "provider.type",
                            OpenAiCompatibleProviderConfiguration.OPENAI_COMPATIBLE_ID)
                        .property("provider.openaiCompatible.endpoint", "https://api.dummy.com/v1")
                        .property("provider.openaiCompatible.model.model", "gpt-4o"),
            OpenAiCompatibleProviderConfiguration.class),
        Arguments.of(
            TEST_CUSTOM_PROVIDER_TYPE,
            (Function<ElementTemplate, ElementTemplate>)
                template ->
                    template
                        .property("provider.type", CustomProviderConfiguration.CUSTOM_ID)
                        .property("provider.providerType", TEST_CUSTOM_PROVIDER_TYPE)
                        .property("provider.parameters", "={foo: \"bar\", count: 42}"),
            CustomProviderConfiguration.class));
  }
}
