/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider;

import static io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.ChatModelProviderSupport.deriveTimeoutSetting;

import dev.langchain4j.model.chat.ChatModel;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureFoundryProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureFoundryProviderConfiguration.AzureAiFoundryConnection;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureFoundryProviderConfiguration.AzureAiFoundryModel.AnthropicModel;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureFoundryProviderConfiguration.AzureAiFoundryModel.OpenAiModel;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.ChatModelProperties;
import io.camunda.connector.agenticai.azurefoundry.AnthropicOnFoundryClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dispatches an {@link AzureFoundryProviderConfiguration} based on the configured model family:
 *
 * <ul>
 *   <li>{@link AnthropicModel} → {@link AnthropicOnFoundryClientFactory} which builds an {@code
 *       anthropic-java} client over a JDK-backed {@code HttpClient} SPI implementation (preserves
 *       authenticated-proxy support).
 *   <li>{@link OpenAiModel} → reuses {@link AzureOpenAiChatModelProvider}'s shared {@code
 *       buildAzureOpenAiChatModel} helper so OpenAI-on-Foundry traffic flows through the same
 *       {@code langchain4j-azure-open-ai} integration as the legacy Azure OpenAI provider.
 * </ul>
 */
public class AzureFoundryChatModelProvider
    implements ChatModelProvider<AzureFoundryProviderConfiguration> {

  private static final Logger LOGGER = LoggerFactory.getLogger(AzureFoundryChatModelProvider.class);

  private final ChatModelProperties config;
  private final AnthropicOnFoundryClientFactory anthropicOnFoundryClientFactory;
  private final AzureOpenAiChatModelProvider azureOpenAiChatModelProvider;

  public AzureFoundryChatModelProvider(
      ChatModelProperties config,
      AnthropicOnFoundryClientFactory anthropicOnFoundryClientFactory,
      AzureOpenAiChatModelProvider azureOpenAiChatModelProvider) {
    this.config = config;
    this.anthropicOnFoundryClientFactory = anthropicOnFoundryClientFactory;
    this.azureOpenAiChatModelProvider = azureOpenAiChatModelProvider;
  }

  @Override
  public String type() {
    return AzureFoundryProviderConfiguration.AZURE_AI_FOUNDRY_ID;
  }

  @Override
  public ChatModel createChatModel(AzureFoundryProviderConfiguration providerConfiguration) {
    AzureAiFoundryConnection conn = providerConfiguration.azureAiFoundry();
    return switch (conn.model()) {
      case AnthropicModel anthropic ->
          anthropicOnFoundryClientFactory.create(
              conn.endpoint(),
              conn.authentication(),
              deriveTimeoutSetting(
                  "Azure AI Foundry Anthropic model call", config, conn.timeouts(), LOGGER),
              anthropic);

      case OpenAiModel openai -> {
        var params = openai.parameters();
        yield azureOpenAiChatModelProvider.buildAzureOpenAiChatModel(
            conn.endpoint(),
            conn.authentication(),
            conn.timeouts(),
            openai.deploymentName(),
            params != null ? params.maxTokens() : null,
            params != null ? params.temperature() : null,
            params != null ? params.topP() : null);
      }
    };
  }
}
