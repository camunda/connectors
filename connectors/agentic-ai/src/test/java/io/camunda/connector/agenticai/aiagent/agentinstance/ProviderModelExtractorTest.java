/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agentinstance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicConnection;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicModel;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureOpenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureOpenAiProviderConfiguration.AzureAuthentication.AzureApiKeyAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureOpenAiProviderConfiguration.AzureOpenAiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureOpenAiProviderConfiguration.AzureOpenAiModel;
import io.camunda.connector.agenticai.aiagent.model.request.provider.BedrockProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.BedrockProviderConfiguration.AwsAuthentication.AwsStaticCredentialsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.BedrockProviderConfiguration.BedrockConnection;
import io.camunda.connector.agenticai.aiagent.model.request.provider.BedrockProviderConfiguration.BedrockModel;
import io.camunda.connector.agenticai.aiagent.model.request.provider.GoogleVertexAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.GoogleVertexAiProviderConfiguration.GoogleVertexAiAuthentication.ApplicationDefaultCredentialsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.GoogleVertexAiProviderConfiguration.GoogleVertexAiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.provider.GoogleVertexAiProviderConfiguration.GoogleVertexAiModel;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiCompatibleProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiCompatibleProviderConfiguration.OpenAiCompatibleAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiCompatibleProviderConfiguration.OpenAiCompatibleConnection;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiCompatibleProviderConfiguration.OpenAiCompatibleModel;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration.OpenAiAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration.OpenAiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration.OpenAiModel;
import org.junit.jupiter.api.Test;

class ProviderModelExtractorTest {

  @Test
  void extractsModelFromAnthropicProvider() {
    final var provider =
        new AnthropicProviderConfiguration(
            new AnthropicConnection(
                null,
                new AnthropicAuthentication("api-key"),
                null,
                new AnthropicModel("claude-sonnet-4-6", null)));

    assertThat(ProviderModelExtractor.extract(provider)).isEqualTo("claude-sonnet-4-6");
  }

  @Test
  void extractsModelFromBedrockProvider() {
    final var provider =
        new BedrockProviderConfiguration(
            new BedrockConnection(
                "eu-west-1",
                null,
                new AwsStaticCredentialsAuthentication("key", "secret"),
                null,
                new BedrockModel("global.anthropic.claude-sonnet-4-6", null)));

    assertThat(ProviderModelExtractor.extract(provider))
        .isEqualTo("global.anthropic.claude-sonnet-4-6");
  }

  @Test
  void extractsDeploymentNameFromAzureOpenAiProvider() {
    final var provider =
        new AzureOpenAiProviderConfiguration(
            new AzureOpenAiConnection(
                "https://my-endpoint.openai.azure.com",
                new AzureApiKeyAuthentication("api-key"),
                null,
                new AzureOpenAiModel("gpt-4o-deployment", null)));

    assertThat(ProviderModelExtractor.extract(provider)).isEqualTo("gpt-4o-deployment");
  }

  @Test
  void extractsModelFromGoogleVertexAiProvider() {
    final var provider =
        new GoogleVertexAiProviderConfiguration(
            new GoogleVertexAiConnection(
                "my-project",
                "us-central1",
                new ApplicationDefaultCredentialsAuthentication(),
                new GoogleVertexAiModel("gemini-1.5-flash", null)));

    assertThat(ProviderModelExtractor.extract(provider)).isEqualTo("gemini-1.5-flash");
  }

  @Test
  void extractsModelFromOpenAiProvider() {
    final var provider =
        new OpenAiProviderConfiguration(
            new OpenAiConnection(
                new OpenAiAuthentication("api-key", null, null),
                null,
                new OpenAiModel("gpt-4o", null)));

    assertThat(ProviderModelExtractor.extract(provider)).isEqualTo("gpt-4o");
  }

  @Test
  void extractsModelFromOpenAiCompatibleProvider() {
    final var provider =
        new OpenAiCompatibleProviderConfiguration(
            new OpenAiCompatibleConnection(
                "https://my-custom-endpoint/v1",
                new OpenAiCompatibleAuthentication("api-key"),
                null,
                null,
                null,
                new OpenAiCompatibleModel("llama-3.1", null)));

    assertThat(ProviderModelExtractor.extract(provider)).isEqualTo("llama-3.1");
  }
}
