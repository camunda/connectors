/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agentinstance;

import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureOpenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.BedrockProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.GoogleVertexAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiCompatibleProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.ProviderConfiguration;
import java.util.Optional;

public final class ProviderModelExtractor {

  public static String extract(ProviderConfiguration provider) {
    var model = extractModelByProviderConfiguration(provider);

    return model.orElseThrow(
        () -> new IllegalStateException("Could not extract model from provider configuration"));
  }

  private static Optional<String> extractModelByProviderConfiguration(
      ProviderConfiguration provider) {
    return switch (provider) {
      case AnthropicProviderConfiguration p ->
          Optional.ofNullable(p.anthropic())
              .map(AnthropicProviderConfiguration.AnthropicConnection::model)
              .map(AnthropicProviderConfiguration.AnthropicModel::model);
      case BedrockProviderConfiguration p ->
          Optional.ofNullable(p.bedrock())
              .map(BedrockProviderConfiguration.BedrockConnection::model)
              .map(BedrockProviderConfiguration.BedrockModel::model);
      case AzureOpenAiProviderConfiguration p ->
          Optional.ofNullable(p.azureOpenAi())
              .map(AzureOpenAiProviderConfiguration.AzureOpenAiConnection::model)
              .map(AzureOpenAiProviderConfiguration.AzureOpenAiModel::deploymentName);
      case GoogleVertexAiProviderConfiguration p ->
          Optional.ofNullable(p.googleVertexAi())
              .map(GoogleVertexAiProviderConfiguration.GoogleVertexAiConnection::model)
              .map(GoogleVertexAiProviderConfiguration.GoogleVertexAiModel::model);
      case OpenAiProviderConfiguration p ->
          Optional.ofNullable(p.openai())
              .map(OpenAiProviderConfiguration.OpenAiConnection::model)
              .map(OpenAiProviderConfiguration.OpenAiModel::model);
      case OpenAiCompatibleProviderConfiguration p ->
          Optional.ofNullable(p.openaiCompatible())
              .map(OpenAiCompatibleProviderConfiguration.OpenAiCompatibleConnection::model)
              .map(OpenAiCompatibleProviderConfiguration.OpenAiCompatibleModel::model);
    };
  }

  private ProviderModelExtractor() {}
}
