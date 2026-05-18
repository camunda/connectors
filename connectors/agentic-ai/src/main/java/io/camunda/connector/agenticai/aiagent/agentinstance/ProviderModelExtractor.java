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

public final class ProviderModelExtractor {

  public static String extract(ProviderConfiguration provider) {
    return switch (provider) {
      case AnthropicProviderConfiguration p -> p.anthropic().model().model();
      case BedrockProviderConfiguration p -> p.bedrock().model().model();
      case AzureOpenAiProviderConfiguration p -> p.azureOpenAi().model().deploymentName();
      case GoogleVertexAiProviderConfiguration p -> p.googleVertexAi().model().model();
      case OpenAiProviderConfiguration p -> p.openai().model().model();
      case OpenAiCompatibleProviderConfiguration p -> p.openaiCompatible().model().model();
    };
  }

  private ProviderModelExtractor() {}
}
