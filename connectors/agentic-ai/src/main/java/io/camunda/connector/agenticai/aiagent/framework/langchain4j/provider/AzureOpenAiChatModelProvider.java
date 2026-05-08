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
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration.OpenAiAuthentication.OpenAiApiKeyAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration.OpenAiAuthentication.OpenAiClientCredentialsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration.OpenAiBackend;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.ChatModelProperties;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LangChain4j bridge provider for the Azure AI Foundry (FOUNDRY) backend of the unified {@link
 * OpenAiProviderConfiguration}. Handles OpenAiProviderConfiguration instances where {@code backend
 * == FOUNDRY} by building an {@link AzureOpenAiChatModel}.
 */
public class AzureOpenAiChatModelProvider
    implements ChatModelProvider<OpenAiProviderConfiguration> {

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
    return OpenAiProviderConfiguration.OPENAI_ID;
  }

  @Override
  public ChatModel createChatModel(OpenAiProviderConfiguration openAi) {
    if (openAi.openai().backend() != OpenAiBackend.FOUNDRY) {
      throw new IllegalArgumentException(
          "AzureOpenAiChatModelProvider only supports OpenAiBackend.FOUNDRY, got: "
              + openAi.openai().backend());
    }
    final var connection = openAi.openai();
    final var builder =
        AzureOpenAiChatModel.builder()
            .endpoint(connection.endpoint())
            .deploymentName(connection.model().model())
            .timeout(
                deriveTimeoutSetting(
                    "Azure OpenAI model call", config, connection.timeouts(), LOGGER));

    proxySupport.createAzureProxyOptions(connection.endpoint()).ifPresent(builder::proxyOptions);

    switch (connection.authentication()) {
      case OpenAiApiKeyAuthentication apiKeyAuth -> builder.apiKey(apiKeyAuth.apiKey());
      case OpenAiClientCredentialsAuthentication auth -> {
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
      Optional.ofNullable(modelParameters.maxCompletionTokens()).ifPresent(builder::maxTokens);
      Optional.ofNullable(modelParameters.temperature()).ifPresent(builder::temperature);
      Optional.ofNullable(modelParameters.topP()).ifPresent(builder::topP);
    }

    return builder.build();
  }
}
