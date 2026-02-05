/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider;

import com.azure.identity.ClientSecretCredentialBuilder;
import dev.langchain4j.model.azure.AzureOpenAiChatModel;
import dev.langchain4j.model.chat.ChatModel;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureOpenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureOpenAiProviderConfiguration.AzureAuthentication.AzureApiKeyAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureOpenAiProviderConfiguration.AzureAuthentication.AzureClientCredentialsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.ProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.TimeoutConfiguration;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties;
import java.time.Duration;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AzureOpenAiChatModelProvider implements ChatModelProvider {

  private static final Logger LOGGER = LoggerFactory.getLogger(AzureOpenAiChatModelProvider.class);

  private final AgenticAiConnectorsConfigurationProperties.ChatModelProperties chatModelProperties;

  public AzureOpenAiChatModelProvider(
      AgenticAiConnectorsConfigurationProperties agenticAiConnectorsConfigurationProperties) {
    this.chatModelProperties = agenticAiConnectorsConfigurationProperties.aiagent().chatModel();
  }

  @Override
  public String getProviderType() {
    return AzureOpenAiProviderConfiguration.AZURE_OPENAI_ID;
  }

  @Override
  public boolean supports(ProviderConfiguration providerConfiguration) {
    return providerConfiguration instanceof AzureOpenAiProviderConfiguration;
  }

  @Override
  public ChatModel createChatModel(ProviderConfiguration providerConfiguration) {
    if (!(providerConfiguration instanceof AzureOpenAiProviderConfiguration azureOpenAi)) {
      throw new IllegalArgumentException(
          "Expected AzureOpenAiProviderConfiguration but got "
              + providerConfiguration.getClass().getSimpleName());
    }

    final var connection = azureOpenAi.azureOpenAi();
    final var builder =
        AzureOpenAiChatModel.builder()
            .endpoint(connection.endpoint())
            .deploymentName(azureOpenAi.azureOpenAi().model().deploymentName())
            .timeout(deriveTimeoutSetting(connection.timeouts()));

    switch (connection.authentication()) {
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

    final var modelParameters = connection.model().parameters();
    if (modelParameters != null) {
      Optional.ofNullable(modelParameters.maxTokens()).ifPresent(builder::maxTokens);
      Optional.ofNullable(modelParameters.temperature()).ifPresent(builder::temperature);
      Optional.ofNullable(modelParameters.topP()).ifPresent(builder::topP);
    }

    return builder.build();
  }

  private Duration deriveTimeoutSetting(TimeoutConfiguration timeoutConfiguration) {
    var derivedTimeout =
        Optional.ofNullable(timeoutConfiguration)
            .map(TimeoutConfiguration::timeout)
            .filter(Duration::isPositive)
            .or(() -> Optional.of(chatModelProperties.api().defaultTimeout()))
            .get();

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(
          "Setting model call timeout to {} for executing requests against the LLM provider",
          derivedTimeout);
    }

    return derivedTimeout;
  }
}
