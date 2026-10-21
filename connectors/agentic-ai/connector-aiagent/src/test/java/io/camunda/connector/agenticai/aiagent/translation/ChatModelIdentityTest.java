/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.translation;

import static io.camunda.connector.agenticai.aiagent.testsupport.AgentConfigurationFixture.agentConfiguration;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.chatmodel.ChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.LimitsConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.SystemPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.request.V1ToV2ProviderConfigurationMapper;
import io.camunda.connector.agenticai.aiagent.request.V1ToV2ProviderConfigurationMapperImpl;
import io.camunda.connector.agenticai.aiagent.request.v1.AnthropicProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.request.v1.AzureOpenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.request.v1.BedrockProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.request.v1.GoogleVertexAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.request.v1.OpenAiCompatibleProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.request.v1.OpenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.request.v2.AnthropicChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicApiBackend;
import io.camunda.connector.agenticai.aiagent.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicApiBackend.AnthropicApi;
import io.camunda.connector.agenticai.aiagent.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicAwsBedrockMantleBackend;
import io.camunda.connector.agenticai.aiagent.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicAwsBedrockMantleBackend.AwsBedrockMantleBackend;
import io.camunda.connector.agenticai.aiagent.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicCustomBackend;
import io.camunda.connector.agenticai.aiagent.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicCustomBackend.CustomBackend;
import io.camunda.connector.agenticai.aiagent.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicFoundryBackend;
import io.camunda.connector.agenticai.aiagent.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicFoundryBackend.FoundryBackend;
import io.camunda.connector.agenticai.aiagent.request.v2.AnthropicChatModelConfiguration.AnthropicConnection;
import io.camunda.connector.agenticai.aiagent.request.v2.AnthropicChatModelConfiguration.AnthropicModel;
import io.camunda.connector.agenticai.aiagent.request.v2.AnthropicCustomEndpointAuthentication;
import io.camunda.connector.agenticai.aiagent.request.v2.AwsAuthentication;
import io.camunda.connector.agenticai.aiagent.request.v2.BedrockConverseChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.request.v2.BedrockConverseChatModelConfiguration.BedrockConverseConnection;
import io.camunda.connector.agenticai.aiagent.request.v2.BedrockConverseChatModelConfiguration.BedrockConverseModel;
import io.camunda.connector.agenticai.aiagent.request.v2.CustomProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.request.v2.FoundryAuthentication;
import io.camunda.connector.agenticai.aiagent.request.v2.GeminiChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.request.v2.GeminiChatModelConfiguration.GeminiBackend.GeminiApiBackend;
import io.camunda.connector.agenticai.aiagent.request.v2.GeminiChatModelConfiguration.GeminiBackend.GeminiApiBackend.GoogleGeminiApi;
import io.camunda.connector.agenticai.aiagent.request.v2.GeminiChatModelConfiguration.GeminiBackend.GeminiVertexAiBackend;
import io.camunda.connector.agenticai.aiagent.request.v2.GeminiChatModelConfiguration.GeminiBackend.GeminiVertexAiBackend.GoogleVertexAi;
import io.camunda.connector.agenticai.aiagent.request.v2.GeminiChatModelConfiguration.GeminiBackend.GoogleVertexAiAuthentication.ApplicationDefaultCredentialsAuthentication;
import io.camunda.connector.agenticai.aiagent.request.v2.GeminiChatModelConfiguration.GeminiConnection;
import io.camunda.connector.agenticai.aiagent.request.v2.GeminiChatModelConfiguration.GeminiModel;
import io.camunda.connector.agenticai.aiagent.request.v2.MistralChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.request.v2.MistralChatModelConfiguration.MistralBackend.MistralApiBackend;
import io.camunda.connector.agenticai.aiagent.request.v2.MistralChatModelConfiguration.MistralBackend.MistralApiBackend.MistralApiConnection;
import io.camunda.connector.agenticai.aiagent.request.v2.MistralChatModelConfiguration.MistralBackend.MistralCustomBackend;
import io.camunda.connector.agenticai.aiagent.request.v2.MistralChatModelConfiguration.MistralConnection;
import io.camunda.connector.agenticai.aiagent.request.v2.MistralChatModelConfiguration.MistralModel;
import io.camunda.connector.agenticai.aiagent.request.v2.OpenAiChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.request.v2.OpenAiChatModelConfiguration.OpenAiApi.OpenAiCompletionsApi;
import io.camunda.connector.agenticai.aiagent.request.v2.OpenAiChatModelConfiguration.OpenAiApi.OpenAiResponsesApi;
import io.camunda.connector.agenticai.aiagent.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiApiBackend;
import io.camunda.connector.agenticai.aiagent.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiApiBackend.OpenAiApiConnection;
import io.camunda.connector.agenticai.aiagent.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiCustomBackend;
import io.camunda.connector.agenticai.aiagent.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiFoundryBackend;
import io.camunda.connector.agenticai.aiagent.request.v2.OpenAiChatModelConfiguration.OpenAiConnection;
import io.camunda.connector.agenticai.aiagent.request.v2.OpenAiChatModelConfiguration.OpenAiModel;
import io.camunda.connector.agenticai.aiagent.request.v2.OpenAiCustomEndpointAuthentication;
import io.camunda.connector.agenticai.aiagent.request.v2.ProviderConfiguration;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The identity of the resolved chat model (provider, model and descriptive provider) feeds the
 * configuration fingerprint, which the agent instance history records. It must stay identical for
 * every provider and backend. The expected values are literals on purpose.
 */
class ChatModelIdentityTest {

  private static final V1ToV2ProviderConfigurationMapper MAPPER =
      new V1ToV2ProviderConfigurationMapperImpl(Duration.ofMinutes(3));

  private static final AwsAuthentication AWS =
      new AwsAuthentication.AwsStaticCredentialsAuthentication("access", "secret");
  private static final FoundryAuthentication FOUNDRY =
      new FoundryAuthentication.ApiKeyAuthentication("key");
  private static final String URL = "https://example.com";

  private static final ChatModelConfigurationTranslator TRANSLATOR =
      new ChatModelConfigurationTranslator(MAPPER, Duration.ofMinutes(3));

  private static ChatModelConfiguration resolve(ProviderConfiguration providerConfiguration) {
    return TRANSLATOR.translate(providerConfiguration);
  }

  private static Arguments v1ChatModel(
      String name,
      io.camunda.connector.agenticai.aiagent.request.v1.ProviderConfiguration configuration,
      String provider,
      String model,
      String descriptiveProvider) {
    return Arguments.of(
        name, resolve(MAPPER.map(configuration)), provider, model, descriptiveProvider);
  }

  private static Arguments chatModel(
      String name,
      ProviderConfiguration configuration,
      String provider,
      String model,
      String descriptiveProvider) {
    return Arguments.of(name, resolve(configuration), provider, model, descriptiveProvider);
  }

  private static AnthropicChatModelConfiguration anthropic(
      AnthropicChatModelConfiguration.AnthropicBackend backend) {
    return new AnthropicChatModelConfiguration(
        new AnthropicConnection(backend, new AnthropicModel("claude-x", null), null));
  }

  private static OpenAiChatModelConfiguration openAi(
      OpenAiChatModelConfiguration.OpenAiApi api,
      OpenAiChatModelConfiguration.OpenAiBackend backend) {
    return new OpenAiChatModelConfiguration(
        new OpenAiConnection(api, backend, new OpenAiModel("gpt-x"), null));
  }

  private static OpenAiChatModelConfiguration.OpenAiBackend openAiApiBackend() {
    return new OpenAiApiBackend(new OpenAiApiConnection("key", null, null, null, null, null, null));
  }

  private static OpenAiChatModelConfiguration.OpenAiBackend openAiFoundryBackend() {
    return new OpenAiFoundryBackend(
        new OpenAiFoundryBackend.FoundryBackend(URL, null, FOUNDRY, null, null, null));
  }

  private static OpenAiChatModelConfiguration.OpenAiBackend openAiCustomBackend() {
    return new OpenAiCustomBackend(
        new OpenAiCustomBackend.CustomBackend(
            URL, null, null, null, new OpenAiCustomEndpointAuthentication.NoAuthentication()));
  }

  static Stream<Arguments> chatModels() {
    return Stream.of(
        chatModel(
            "anthropic api",
            anthropic(new AnthropicApiBackend(new AnthropicApi("key", null, null, null, null))),
            "anthropic",
            "claude-x",
            "anthropic/anthropic-api"),
        chatModel(
            "anthropic on bedrock mantle",
            anthropic(
                new AnthropicAwsBedrockMantleBackend(
                    new AwsBedrockMantleBackend("us-east-1", null, AWS, null, null, null))),
            "anthropic",
            "claude-x",
            "anthropic/aws-bedrock-mantle"),
        chatModel(
            "anthropic on foundry",
            anthropic(
                new AnthropicFoundryBackend(new FoundryBackend(URL, FOUNDRY, null, null, null))),
            "anthropic",
            "claude-x",
            "anthropic/foundry"),
        chatModel(
            "anthropic custom",
            anthropic(
                new AnthropicCustomBackend(
                    new CustomBackend(
                        URL,
                        null,
                        null,
                        null,
                        new AnthropicCustomEndpointAuthentication.NoAuthentication()))),
            "anthropic",
            "claude-x",
            "anthropic/custom"),
        chatModel(
            "bedrock converse",
            new BedrockConverseChatModelConfiguration(
                new BedrockConverseConnection(
                    "us-east-1",
                    null,
                    AWS,
                    null,
                    null,
                    null,
                    null,
                    new BedrockConverseModel("nova-x", null))),
            "bedrock",
            "nova-x",
            "bedrock"),
        chatModel(
            "openai responses on openai",
            openAi(new OpenAiResponsesApi(null), openAiApiBackend()),
            "openai",
            "gpt-x",
            "openai/responses/openai-api"),
        chatModel(
            "openai completions on openai",
            openAi(new OpenAiCompletionsApi(null), openAiApiBackend()),
            "openai",
            "gpt-x",
            "openai/completions/openai-api"),
        chatModel(
            "openai responses on foundry",
            openAi(new OpenAiResponsesApi(null), openAiFoundryBackend()),
            "openai",
            "gpt-x",
            "openai/responses/foundry"),
        chatModel(
            "openai completions on foundry",
            openAi(new OpenAiCompletionsApi(null), openAiFoundryBackend()),
            "openai",
            "gpt-x",
            "openai/completions/foundry"),
        chatModel(
            "openai responses custom",
            openAi(new OpenAiResponsesApi(null), openAiCustomBackend()),
            "openai",
            "gpt-x",
            "openai/responses/custom"),
        chatModel(
            "openai completions custom",
            openAi(new OpenAiCompletionsApi(null), openAiCustomBackend()),
            "openai",
            "gpt-x",
            "openai/completions/custom"),
        chatModel(
            "gemini api",
            new GeminiChatModelConfiguration(
                new GeminiConnection(
                    new GeminiApiBackend(new GoogleGeminiApi("key", null)),
                    new GeminiModel("gemini-x", null),
                    null)),
            "google-gemini",
            "gemini-x",
            "google-gemini/google-gemini-api"),
        chatModel(
            "gemini on vertex ai",
            new GeminiChatModelConfiguration(
                new GeminiConnection(
                    new GeminiVertexAiBackend(
                        new GoogleVertexAi(
                            "project",
                            "us-central1",
                            null,
                            new ApplicationDefaultCredentialsAuthentication())),
                    new GeminiModel("gemini-x", null),
                    null)),
            "google-gemini",
            "gemini-x",
            "google-gemini/google-vertex-ai"),
        chatModel(
            "mistral api",
            new MistralChatModelConfiguration(
                new MistralConnection(
                    new MistralApiBackend(new MistralApiConnection("key", null, null, null, null)),
                    new MistralModel("mistral-x"),
                    null,
                    null)),
            "mistral",
            "mistral-x",
            "mistral/mistral-api"),
        chatModel(
            "mistral custom",
            new MistralChatModelConfiguration(
                new MistralConnection(
                    new MistralCustomBackend(
                        new MistralCustomBackend.CustomBackend(
                            URL,
                            null,
                            null,
                            null,
                            new OpenAiCustomEndpointAuthentication.NoAuthentication())),
                    new MistralModel("mistral-x"),
                    null,
                    null)),
            "mistral",
            "mistral-x",
            "mistral/custom"),
        v1ChatModel(
            "v1 anthropic",
            new AnthropicProviderConfiguration(
                new AnthropicProviderConfiguration.AnthropicConnection(
                    null,
                    new AnthropicProviderConfiguration.AnthropicAuthentication("key"),
                    null,
                    new AnthropicProviderConfiguration.AnthropicModel("claude-x", null))),
            "anthropic",
            "claude-x",
            "anthropic/anthropic-api"),
        v1ChatModel(
            "v1 bedrock",
            new BedrockProviderConfiguration(
                new BedrockProviderConfiguration.BedrockConnection(
                    "us-east-1",
                    null,
                    new BedrockProviderConfiguration.AwsAuthentication
                        .AwsStaticCredentialsAuthentication("access", "secret"),
                    null,
                    new BedrockProviderConfiguration.BedrockModel("nova-x", null))),
            "bedrock",
            "nova-x",
            "bedrock"),
        v1ChatModel(
            "v1 azure openai",
            new AzureOpenAiProviderConfiguration(
                new AzureOpenAiProviderConfiguration.AzureOpenAiConnection(
                    URL,
                    new AzureOpenAiProviderConfiguration.AzureAuthentication
                        .AzureApiKeyAuthentication("key"),
                    null,
                    new AzureOpenAiProviderConfiguration.AzureOpenAiModel("deployment-x", null))),
            "openai",
            "deployment-x",
            "openai/completions/foundry"),
        v1ChatModel(
            "v1 google vertex ai",
            new GoogleVertexAiProviderConfiguration(
                new GoogleVertexAiProviderConfiguration.GoogleVertexAiConnection(
                    "project",
                    "us-central1",
                    null,
                    new GoogleVertexAiProviderConfiguration.GoogleVertexAiAuthentication
                        .ApplicationDefaultCredentialsAuthentication(),
                    null,
                    new GoogleVertexAiProviderConfiguration.GoogleVertexAiModel("gemini-x", null))),
            "google-gemini",
            "gemini-x",
            "google-gemini/google-vertex-ai"),
        v1ChatModel(
            "v1 openai",
            new OpenAiProviderConfiguration(
                new OpenAiProviderConfiguration.OpenAiConnection(
                    new OpenAiProviderConfiguration.OpenAiAuthentication("key", null, null),
                    null,
                    new OpenAiProviderConfiguration.OpenAiModel("gpt-x", null))),
            "openai",
            "gpt-x",
            "openai/completions/openai-api"),
        v1ChatModel(
            "v1 openai compatible",
            new OpenAiCompatibleProviderConfiguration(
                new OpenAiCompatibleProviderConfiguration.OpenAiCompatibleConnection(
                    URL,
                    null,
                    null,
                    null,
                    null,
                    new OpenAiCompatibleProviderConfiguration.OpenAiCompatibleModel(
                        "model-x", null))),
            "openai",
            "model-x",
            "openai/completions/custom"),
        chatModel(
            "custom provider",
            new CustomProviderConfiguration("my-provider", "my-model", null),
            "my-provider",
            "my-model",
            "my-provider"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("chatModels")
  void keepsTheIdentityOfTheChatModel(
      String name,
      ChatModelConfiguration chatModel,
      String provider,
      String model,
      String descriptiveProvider) {
    assertThat(chatModel.provider()).isEqualTo(provider);
    assertThat(chatModel.model()).isEqualTo(model);
    assertThat(chatModel.descriptiveProvider()).isEqualTo(descriptiveProvider);
  }

  @Test
  void pinsTheFingerprintFormat() {
    final var configuration =
        agentConfiguration()
            .chatModel(resolve(new CustomProviderConfiguration("p", "m", null)))
            .systemPrompt(new SystemPromptConfiguration("Be nice."))
            .limits(new LimitsConfiguration(10))
            .build();

    assertThat(configuration.fingerprint())
        .isEqualTo("5dc07aee9c3d690cfa6839c41b66163205df700c9080c6ee4d888aa693d449b1");
  }
}
