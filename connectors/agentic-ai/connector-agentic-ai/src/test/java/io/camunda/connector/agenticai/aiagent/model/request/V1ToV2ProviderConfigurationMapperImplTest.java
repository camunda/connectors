/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.request.v1.AnthropicProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v1.AnthropicProviderConfiguration.AnthropicAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.v1.AnthropicProviderConfiguration.AnthropicConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v1.AnthropicProviderConfiguration.AnthropicModel;
import io.camunda.connector.agenticai.aiagent.model.request.v1.AnthropicProviderConfiguration.AnthropicModel.AnthropicModelParameters;
import io.camunda.connector.agenticai.aiagent.model.request.v1.AzureOpenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v1.AzureOpenAiProviderConfiguration.AzureAuthentication.AzureApiKeyAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.v1.AzureOpenAiProviderConfiguration.AzureAuthentication.AzureClientCredentialsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.v1.AzureOpenAiProviderConfiguration.AzureOpenAiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v1.AzureOpenAiProviderConfiguration.AzureOpenAiModel;
import io.camunda.connector.agenticai.aiagent.model.request.v1.AzureOpenAiProviderConfiguration.AzureOpenAiModel.AzureOpenAiModelParameters;
import io.camunda.connector.agenticai.aiagent.model.request.v1.BedrockProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v1.BedrockProviderConfiguration.AwsAuthentication.AwsApiKeyAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.v1.BedrockProviderConfiguration.AwsAuthentication.AwsDefaultCredentialsChainAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.v1.BedrockProviderConfiguration.AwsAuthentication.AwsStaticCredentialsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.v1.BedrockProviderConfiguration.BedrockConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v1.BedrockProviderConfiguration.BedrockModel;
import io.camunda.connector.agenticai.aiagent.model.request.v1.BedrockProviderConfiguration.BedrockModel.BedrockModelParameters;
import io.camunda.connector.agenticai.aiagent.model.request.v1.GoogleVertexAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v1.GoogleVertexAiProviderConfiguration.GoogleVertexAiAuthentication.ApplicationDefaultCredentialsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.v1.GoogleVertexAiProviderConfiguration.GoogleVertexAiAuthentication.ServiceAccountCredentialsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.v1.GoogleVertexAiProviderConfiguration.GoogleVertexAiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v1.GoogleVertexAiProviderConfiguration.GoogleVertexAiModel;
import io.camunda.connector.agenticai.aiagent.model.request.v1.GoogleVertexAiProviderConfiguration.GoogleVertexAiModel.GoogleVertexAiModelParameters;
import io.camunda.connector.agenticai.aiagent.model.request.v1.OpenAiCompatibleProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v1.OpenAiCompatibleProviderConfiguration.OpenAiCompatibleAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.v1.OpenAiCompatibleProviderConfiguration.OpenAiCompatibleConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v1.OpenAiCompatibleProviderConfiguration.OpenAiCompatibleModel;
import io.camunda.connector.agenticai.aiagent.model.request.v1.OpenAiCompatibleProviderConfiguration.OpenAiCompatibleModel.OpenAiCompatibleModelParameters;
import io.camunda.connector.agenticai.aiagent.model.request.v1.OpenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v1.OpenAiProviderConfiguration.OpenAiAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.v1.OpenAiProviderConfiguration.OpenAiModel.OpenAiModelParameters;
import io.camunda.connector.agenticai.aiagent.model.request.v1.shared.TimeoutConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicApiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicApiBackend.AnthropicApi;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AwsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.v2.BedrockConverseChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.BedrockConverseChatModelConfiguration.BedrockConverseConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.BedrockConverseChatModelConfiguration.BedrockConverseModel;
import io.camunda.connector.agenticai.aiagent.model.request.v2.BedrockConverseChatModelConfiguration.BedrockConverseModel.BedrockConverseModelParameters;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiBackend.GeminiVertexAiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiBackend.GeminiVertexAiBackend.GoogleVertexAi;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiBackend.GoogleVertexAiAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiModel;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiModel.GeminiModelParameters;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiApi.OpenAiCompletionsApi;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiApi.OpenAiCompletionsApi.CompletionsParameters;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.FoundryAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiApiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiApiBackend.OpenAiApiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiCustomBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiCustomBackend.CustomBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiFoundryBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiFoundryBackend.FoundryBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiModel;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiCustomEndpointAuthentication.ApiKeyAuthentication;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class V1ToV2ProviderConfigurationMapperImplTest {

  private final V1ToV2ProviderConfigurationMapper mapper =
      new V1ToV2ProviderConfigurationMapperImpl();

  @Test
  void mapsAnthropicProviderConfigurationWithParameters() {
    final var source =
        new AnthropicProviderConfiguration(
            new AnthropicConnection(
                "https://custom.anthropic.example",
                new AnthropicAuthentication("anthropic-api-key"),
                new TimeoutConfiguration(Duration.ofSeconds(30)),
                new AnthropicModel(
                    "claude-sonnet-4-6", new AnthropicModelParameters(1024, 0.5, 0.9, 40))));

    final var result = mapper.map(source);

    final var expected =
        new AnthropicChatModelConfiguration(
            new AnthropicChatModelConfiguration.AnthropicConnection(
                new AnthropicApiBackend(
                    new AnthropicApi(
                        "anthropic-api-key", "https://custom.anthropic.example", null, null, null)),
                new AnthropicChatModelConfiguration.AnthropicModel(
                    "claude-sonnet-4-6",
                    new AnthropicChatModelConfiguration.AnthropicModel.AnthropicModelParameters(
                        null, null, null, 1024, 0.5, 0.9, 40)),
                new TimeoutConfiguration(Duration.ofSeconds(30))));

    assertThat(result).isEqualTo(expected);
  }

  @Test
  void mapsAnthropicProviderConfigurationWithNullParameters() {
    final var source =
        new AnthropicProviderConfiguration(
            new AnthropicConnection(
                null,
                new AnthropicAuthentication("anthropic-api-key"),
                null,
                new AnthropicModel("claude-3", null)));

    final var result = mapper.map(source);

    final var expected =
        new AnthropicChatModelConfiguration(
            new AnthropicChatModelConfiguration.AnthropicConnection(
                new AnthropicApiBackend(
                    new AnthropicApi("anthropic-api-key", null, null, null, null)),
                new AnthropicChatModelConfiguration.AnthropicModel("claude-3", null),
                null));

    assertThat(result).isEqualTo(expected);
  }

  @Test
  void stripsAnthropicApiVersionSuffixFromEndpoint() {
    final var source =
        new AnthropicProviderConfiguration(
            new AnthropicConnection(
                "https://proxy.example.com/v1/",
                new AnthropicAuthentication("anthropic-api-key"),
                null,
                new AnthropicModel("claude-3", null)));

    final var result = mapper.map(source);

    final var expected =
        new AnthropicChatModelConfiguration(
            new AnthropicChatModelConfiguration.AnthropicConnection(
                new AnthropicApiBackend(
                    new AnthropicApi(
                        "anthropic-api-key", "https://proxy.example.com", null, null, null)),
                new AnthropicChatModelConfiguration.AnthropicModel("claude-3", null),
                null));

    assertThat(result).isEqualTo(expected);
  }

  @Test
  void mapsOpenAiProviderConfigurationWithParameters() {
    final var source =
        new OpenAiProviderConfiguration(
            new OpenAiProviderConfiguration.OpenAiConnection(
                new OpenAiAuthentication("openai-key", "org-1", "proj-1"),
                new TimeoutConfiguration(Duration.ofSeconds(45)),
                new OpenAiProviderConfiguration.OpenAiModel(
                    "gpt-4o", new OpenAiModelParameters(2048, 0.7, 0.8))));

    final var result = mapper.map(source);

    final var expected =
        new OpenAiChatModelConfiguration(
            new OpenAiConnection(
                new OpenAiCompletionsApi(new CompletionsParameters(2048, null, 0.7, 0.8)),
                new OpenAiApiBackend(
                    new OpenAiApiConnection(
                        "openai-key", "org-1", "proj-1", null, null, null, null)),
                new OpenAiModel("gpt-4o"),
                new TimeoutConfiguration(Duration.ofSeconds(45))));

    assertThat(result).isEqualTo(expected);
  }

  @Test
  void mapsOpenAiCompatibleProviderConfigurationWithApiKeyPresent() {
    final var source =
        new OpenAiCompatibleProviderConfiguration(
            new OpenAiCompatibleConnection(
                "https://compat.example/v1",
                new OpenAiCompatibleAuthentication("compat-key"),
                Map.of("X-Custom", "value1"),
                Map.of("api-version", "2026-01-01"),
                new TimeoutConfiguration(Duration.ofSeconds(20)),
                new OpenAiCompatibleModel(
                    "llama-70b",
                    new OpenAiCompatibleModelParameters(512, 0.3, 0.95, Map.of("seed", 42)))));

    final var result = mapper.map(source);

    final var expected =
        new OpenAiChatModelConfiguration(
            new OpenAiConnection(
                new OpenAiCompletionsApi(new CompletionsParameters(512, null, 0.3, 0.95)),
                new OpenAiCustomBackend(
                    new CustomBackend(
                        "https://compat.example/v1",
                        Map.of("X-Custom", "value1"),
                        Map.of("api-version", "2026-01-01"),
                        Map.of("seed", 42),
                        new ApiKeyAuthentication("compat-key"))),
                new OpenAiModel("llama-70b"),
                new TimeoutConfiguration(Duration.ofSeconds(20))));

    assertThat(result).isEqualTo(expected);
  }

  @Test
  void mapsOpenAiCompatibleProviderConfiguration_liftsBearerTokenFromAuthorizationHeader() {
    final var source =
        new OpenAiCompatibleProviderConfiguration(
            new OpenAiCompatibleConnection(
                "https://compat.example/v1",
                new OpenAiCompatibleAuthentication(""),
                Map.of("Authorization", "Bearer xyz-token", "X-Other", "keep-me"),
                null,
                null,
                new OpenAiCompatibleModel("llama-70b", null)));

    final var result = mapper.map(source);

    final var expected =
        new OpenAiChatModelConfiguration(
            new OpenAiConnection(
                new OpenAiCompletionsApi(null),
                new OpenAiCustomBackend(
                    new CustomBackend(
                        "https://compat.example/v1",
                        Map.of("X-Other", "keep-me"),
                        null,
                        null,
                        new ApiKeyAuthentication("xyz-token"))),
                new OpenAiModel("llama-70b"),
                null));

    assertThat(result).isEqualTo(expected);
  }

  @Test
  void mapsOpenAiCompatibleProviderConfiguration_usesPlaceholderWhenNoApiKeyOrAuthHeader() {
    final var source =
        new OpenAiCompatibleProviderConfiguration(
            new OpenAiCompatibleConnection(
                "https://compat.example/v1",
                new OpenAiCompatibleAuthentication(""),
                Map.of("X-Other", "value"),
                null,
                null,
                new OpenAiCompatibleModel("llama-70b", null)));

    final var result = mapper.map(source);

    final var expected =
        new OpenAiChatModelConfiguration(
            new OpenAiConnection(
                new OpenAiCompletionsApi(null),
                new OpenAiCustomBackend(
                    new CustomBackend(
                        "https://compat.example/v1",
                        Map.of("X-Other", "value"),
                        null,
                        null,
                        new ApiKeyAuthentication(
                            V1ToV2ProviderConfigurationMapperImpl.MISSING_API_KEY_PLACEHOLDER))),
                new OpenAiModel("llama-70b"),
                null));

    assertThat(result).isEqualTo(expected);
  }

  @Test
  void
      mapsOpenAiCompatibleProviderConfiguration_keepsNonBearerAuthorizationHeaderAndUsesPlaceholder() {
    final var source =
        new OpenAiCompatibleProviderConfiguration(
            new OpenAiCompatibleConnection(
                "https://compat.example/v1",
                new OpenAiCompatibleAuthentication(""),
                Map.of("Authorization", "Basic abc123"),
                null,
                null,
                new OpenAiCompatibleModel("llama-70b", null)));

    final var result = mapper.map(source);

    final var expected =
        new OpenAiChatModelConfiguration(
            new OpenAiConnection(
                new OpenAiCompletionsApi(null),
                new OpenAiCustomBackend(
                    new CustomBackend(
                        "https://compat.example/v1",
                        Map.of("Authorization", "Basic abc123"),
                        null,
                        null,
                        new ApiKeyAuthentication(
                            V1ToV2ProviderConfigurationMapperImpl.MISSING_API_KEY_PLACEHOLDER))),
                new OpenAiModel("llama-70b"),
                null));

    assertThat(result).isEqualTo(expected);
  }

  @Test
  void mapsBedrockProviderConfigurationWithParameters() {
    final var source =
        new BedrockProviderConfiguration(
            new BedrockConnection(
                "eu-west-1",
                "https://vpce.example",
                new AwsStaticCredentialsAuthentication("AKIA-access", "secret-key"),
                new TimeoutConfiguration(Duration.ofSeconds(15)),
                new BedrockModel(
                    "global.anthropic.claude-sonnet-4-6",
                    new BedrockModelParameters(4096, 0.6, 0.85))));

    final var result = mapper.map(source);

    final var expected =
        new BedrockConverseChatModelConfiguration(
            new BedrockConverseConnection(
                "eu-west-1",
                "https://vpce.example",
                new AwsAuthentication.AwsStaticCredentialsAuthentication(
                    "AKIA-access", "secret-key"),
                null,
                null,
                null,
                new TimeoutConfiguration(Duration.ofSeconds(15)),
                new BedrockConverseModel(
                    "global.anthropic.claude-sonnet-4-6",
                    new BedrockConverseModelParameters(null, 4096, 0.6, 0.85))));

    assertThat(result).isEqualTo(expected);
  }

  @Test
  void mapsBedrockProviderConfigurationWithNullParameters() {
    final var source =
        new BedrockProviderConfiguration(
            new BedrockConnection(
                "us-east-1",
                null,
                new AwsStaticCredentialsAuthentication("AKIA-access", "secret-key"),
                null,
                new BedrockModel("nova-lite", null)));

    final var result = mapper.map(source);

    final var expected =
        new BedrockConverseChatModelConfiguration(
            new BedrockConverseConnection(
                "us-east-1",
                null,
                new AwsAuthentication.AwsStaticCredentialsAuthentication(
                    "AKIA-access", "secret-key"),
                null,
                null,
                null,
                null,
                new BedrockConverseModel("nova-lite", null)));

    assertThat(result).isEqualTo(expected);
  }

  @Test
  void mapsAwsStaticCredentialsAuthentication() {
    final var source =
        new BedrockProviderConfiguration(
            new BedrockConnection(
                "eu-west-1",
                null,
                new AwsStaticCredentialsAuthentication("AKIA-access", "secret-key"),
                null,
                new BedrockModel("nova-lite", null)));

    final var result = mapper.map(source);

    assertThat(((BedrockConverseChatModelConfiguration) result).bedrock().authentication())
        .isEqualTo(
            new AwsAuthentication.AwsStaticCredentialsAuthentication("AKIA-access", "secret-key"));
  }

  @Test
  void mapsAwsApiKeyAuthentication() {
    final var source =
        new BedrockProviderConfiguration(
            new BedrockConnection(
                "eu-west-1",
                null,
                new AwsApiKeyAuthentication("bedrock-api-key"),
                null,
                new BedrockModel("nova-lite", null)));

    final var result = mapper.map(source);

    assertThat(((BedrockConverseChatModelConfiguration) result).bedrock().authentication())
        .isEqualTo(new AwsAuthentication.AwsApiKeyAuthentication("bedrock-api-key"));
  }

  @Test
  void mapsAwsDefaultCredentialsChainAuthentication() {
    final var source =
        new BedrockProviderConfiguration(
            new BedrockConnection(
                "eu-west-1",
                null,
                new AwsDefaultCredentialsChainAuthentication(),
                null,
                new BedrockModel("nova-lite", null)));

    final var result = mapper.map(source);

    assertThat(((BedrockConverseChatModelConfiguration) result).bedrock().authentication())
        .isEqualTo(new AwsAuthentication.AwsDefaultCredentialsChainAuthentication());
  }

  @Test
  void mapsAzureOpenAiProviderConfigurationWithApiKeyAuthenticationAndParameters() {
    final var source =
        new AzureOpenAiProviderConfiguration(
            new AzureOpenAiConnection(
                "https://my-resource.openai.azure.com",
                new AzureApiKeyAuthentication("azure-api-key"),
                new TimeoutConfiguration(Duration.ofSeconds(25)),
                new AzureOpenAiModel(
                    "gpt-4o-deployment", new AzureOpenAiModelParameters(2048, 0.6, 0.85))));

    final var result = mapper.map(source);

    final var expected =
        new OpenAiChatModelConfiguration(
            new OpenAiConnection(
                new OpenAiCompletionsApi(new CompletionsParameters(2048, null, 0.6, 0.85)),
                new OpenAiFoundryBackend(
                    new FoundryBackend(
                        "https://my-resource.openai.azure.com",
                        null,
                        new FoundryAuthentication.ApiKeyAuthentication("azure-api-key"),
                        null,
                        null,
                        null)),
                new OpenAiModel("gpt-4o-deployment"),
                new TimeoutConfiguration(Duration.ofSeconds(25))));

    assertThat(result).isEqualTo(expected);
  }

  @Test
  void mapsAzureOpenAiProviderConfigurationWithClientCredentialsAuthentication() {
    final var source =
        new AzureOpenAiProviderConfiguration(
            new AzureOpenAiConnection(
                "https://my-resource.openai.azure.com",
                new AzureClientCredentialsAuthentication(
                    "client-id", "client-secret", "tenant-id", "https://login.contoso.com"),
                null,
                new AzureOpenAiModel("gpt-4o-deployment", null)));

    final var result = mapper.map(source);

    final var expected =
        new OpenAiChatModelConfiguration(
            new OpenAiConnection(
                new OpenAiCompletionsApi(null),
                new OpenAiFoundryBackend(
                    new FoundryBackend(
                        "https://my-resource.openai.azure.com",
                        null,
                        new FoundryAuthentication.ClientCredentialsAuthentication(
                            "client-id",
                            "client-secret",
                            "tenant-id",
                            "https://login.contoso.com",
                            null),
                        null,
                        null,
                        null)),
                new OpenAiModel("gpt-4o-deployment"),
                null));

    assertThat(result).isEqualTo(expected);
  }

  @Test
  void mapsAzureOpenAiProviderConfigurationWithNullParameters() {
    final var source =
        new AzureOpenAiProviderConfiguration(
            new AzureOpenAiConnection(
                "https://my-resource.openai.azure.com",
                new AzureApiKeyAuthentication("azure-api-key"),
                null,
                new AzureOpenAiModel("gpt-4o-deployment", null)));

    final var result = mapper.map(source);

    final var expected =
        new OpenAiChatModelConfiguration(
            new OpenAiConnection(
                new OpenAiCompletionsApi(null),
                new OpenAiFoundryBackend(
                    new FoundryBackend(
                        "https://my-resource.openai.azure.com",
                        null,
                        new FoundryAuthentication.ApiKeyAuthentication("azure-api-key"),
                        null,
                        null,
                        null)),
                new OpenAiModel("gpt-4o-deployment"),
                null));

    assertThat(result).isEqualTo(expected);
  }

  @Test
  void mapsGoogleVertexAiProviderConfigurationWithServiceAccountCredentialsAndParameters() {
    final float sourceTemperature = 0.5f;
    final float sourceTopP = 0.9f;

    final var source =
        new GoogleVertexAiProviderConfiguration(
            new GoogleVertexAiConnection(
                "my-gcp-project",
                "us-central1",
                new ServiceAccountCredentialsAuthentication("{\"type\":\"service_account\"}"),
                new GoogleVertexAiModel(
                    "gemini-3-pro-preview",
                    new GoogleVertexAiModelParameters(1024, sourceTemperature, sourceTopP, 40))));

    final var result = mapper.map(source);

    // v1 vertex temperature/topP are Float; v2 GeminiModelParameters uses Double. The expected
    // values below use the natural widening of the source floats (not literal double constants)
    // because a float like 0.9f is not exactly representable and widens to
    // 0.8999999761581421d, not 0.9d.
    final var expected =
        new GeminiChatModelConfiguration(
            new GeminiConnection(
                new GeminiVertexAiBackend(
                    new GoogleVertexAi(
                        "my-gcp-project",
                        "us-central1",
                        null,
                        new GoogleVertexAiAuthentication.ServiceAccountCredentialsAuthentication(
                            "{\"type\":\"service_account\"}"))),
                new GeminiModel(
                    "gemini-3-pro-preview",
                    new GeminiModelParameters(
                        1024, (double) sourceTemperature, (double) sourceTopP, 40, null)),
                null));

    assertThat(result).isEqualTo(expected);

    final var geminiModelParameters =
        ((GeminiChatModelConfiguration) result).googleGemini().model().parameters();
    assertThat(geminiModelParameters).isNotNull();
    assertThat(geminiModelParameters.temperature()).isEqualTo((double) sourceTemperature);
    assertThat(geminiModelParameters.topP()).isEqualTo((double) sourceTopP);
  }

  @Test
  void mapsGoogleVertexAiProviderConfigurationWithApplicationDefaultCredentials() {
    final var source =
        new GoogleVertexAiProviderConfiguration(
            new GoogleVertexAiConnection(
                "my-gcp-project",
                "us-central1",
                new ApplicationDefaultCredentialsAuthentication(),
                new GoogleVertexAiModel("gemini-3-pro-preview", null)));

    final var result = mapper.map(source);

    final var expected =
        new GeminiChatModelConfiguration(
            new GeminiConnection(
                new GeminiVertexAiBackend(
                    new GoogleVertexAi(
                        "my-gcp-project",
                        "us-central1",
                        null,
                        new GoogleVertexAiAuthentication
                            .ApplicationDefaultCredentialsAuthentication())),
                new GeminiModel("gemini-3-pro-preview", null),
                null));

    assertThat(result).isEqualTo(expected);
  }

  @Test
  void mapsGoogleVertexAiProviderConfigurationWithNullParameters() {
    final var source =
        new GoogleVertexAiProviderConfiguration(
            new GoogleVertexAiConnection(
                "my-gcp-project",
                "us-central1",
                new ServiceAccountCredentialsAuthentication("{\"type\":\"service_account\"}"),
                new GoogleVertexAiModel("gemini-3-pro-preview", null)));

    final var result = mapper.map(source);

    final var expected =
        new GeminiChatModelConfiguration(
            new GeminiConnection(
                new GeminiVertexAiBackend(
                    new GoogleVertexAi(
                        "my-gcp-project",
                        "us-central1",
                        null,
                        new GoogleVertexAiAuthentication.ServiceAccountCredentialsAuthentication(
                            "{\"type\":\"service_account\"}"))),
                new GeminiModel("gemini-3-pro-preview", null),
                null));

    assertThat(result).isEqualTo(expected);
  }
}
