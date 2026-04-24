/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider;

import static io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.ChatModelProviderSupport.deriveTimeoutSetting;

import com.azure.identity.ClientSecretCredentialBuilder;
import dev.langchain4j.model.azure.AzureOpenAiChatModel;
import dev.langchain4j.model.chat.ChatModel;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.ChatModelHttpProxySupport;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureOpenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.AzureAuthentication.AzureApiKeyAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.AzureAuthentication.AzureClientCredentialsAuthentication;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.ChatModelProperties;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AzureOpenAiChatModelProvider
    implements ChatModelProvider<AzureOpenAiProviderConfiguration> {

  private static final Logger LOGGER = LoggerFactory.getLogger(AzureOpenAiChatModelProvider.class);

  private final ChatModelProperties config;
  private final ChatModelHttpProxySupport proxySupport;

  public AzureOpenAiChatModelProvider(
      ChatModelProperties config, ChatModelHttpProxySupport proxySupport) {
    this.config = config;
    this.proxySupport = proxySupport;
  }

  @Override
  public String type() {
    return AzureOpenAiProviderConfiguration.AZURE_OPENAI_ID;
  }

  @Override
  public ChatModel createChatModel(AzureOpenAiProviderConfiguration azureOpenAi) {
    final var connection = azureOpenAi.azureOpenAi();
    final var modelParameters = connection.model().parameters();
    return buildAzureOpenAiChatModel(
        connection.endpoint(),
        connection.authentication(),
        connection.timeouts(),
        connection.model().deploymentName(),
        modelParameters != null ? modelParameters.maxTokens() : null,
        modelParameters != null ? modelParameters.temperature() : null,
        modelParameters != null ? modelParameters.topP() : null);
  }

  /**
   * Shared helper used by this provider for the legacy {@code azureOpenAi} configuration and by the
   * Azure AI Foundry provider for its OpenAI model family. Both flow through the same {@code
   * langchain4j-azure-open-ai} integration.
   */
  AzureOpenAiChatModel buildAzureOpenAiChatModel(
      String endpoint,
      io.camunda.connector.agenticai.aiagent.model.request.provider.shared.AzureAuthentication
          authentication,
      io.camunda.connector.agenticai.aiagent.model.request.provider.shared.TimeoutConfiguration
          timeouts,
      String deploymentName,
      Integer maxTokens,
      Double temperature,
      Double topP) {

    final var builder =
        AzureOpenAiChatModel.builder()
            .endpoint(endpoint)
            .deploymentName(deploymentName)
            .timeout(deriveTimeoutSetting("Azure OpenAI model call", config, timeouts, LOGGER));

    proxySupport.createAzureProxyOptions(endpoint).ifPresent(builder::proxyOptions);

    switch (authentication) {
      case AzureApiKeyAuthentication azureApiKeyAuthentication ->
          builder.apiKey(azureApiKeyAuthentication.apiKey());
      case AzureClientCredentialsAuthentication auth -> {
        ClientSecretCredentialBuilder clientSecretCredentialBuilder =
            new ClientSecretCredentialBuilder()
                .clientId(auth.clientId())
                .clientSecret(auth.clientSecret())
                .tenantId(auth.tenantId());
        if (StringUtils.isNotBlank(auth.authorityHost())) {
          clientSecretCredentialBuilder.authorityHost(auth.authorityHost());
        }
        builder.tokenCredential(clientSecretCredentialBuilder.build());
      }
    }

    Optional.ofNullable(maxTokens).ifPresent(builder::maxTokens);
    Optional.ofNullable(temperature).ifPresent(builder::temperature);
    Optional.ofNullable(topP).ifPresent(builder::topP);

    return builder.build();
  }
}
