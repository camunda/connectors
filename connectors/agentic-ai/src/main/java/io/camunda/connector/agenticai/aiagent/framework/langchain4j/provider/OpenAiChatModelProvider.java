/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.ProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.TimeoutConfiguration;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenAiChatModelProvider implements ChatModelProvider {

  private static final Logger LOGGER = LoggerFactory.getLogger(OpenAiChatModelProvider.class);

  private final AgenticAiConnectorsConfigurationProperties.ChatModelProperties chatModelProperties;

  public OpenAiChatModelProvider(
      AgenticAiConnectorsConfigurationProperties agenticAiConnectorsConfigurationProperties) {
    this.chatModelProperties = agenticAiConnectorsConfigurationProperties.aiagent().chatModel();
  }

  @Override
  public String getProviderType() {
    return OpenAiProviderConfiguration.TYPE;
  }

  @Override
  public boolean supports(ProviderConfiguration providerConfiguration) {
    return providerConfiguration instanceof OpenAiProviderConfiguration;
  }

  @Override
  public ChatModel createChatModel(ProviderConfiguration providerConfiguration) {
    if (!(providerConfiguration instanceof OpenAiProviderConfiguration openai)) {
      throw new IllegalArgumentException(
          "Expected OpenAiProviderConfiguration but got "
              + providerConfiguration.getClass().getSimpleName());
    }

    final var connection = openai.openai();

    final var builder =
        OpenAiChatModel.builder()
            .apiKey(connection.authentication().apiKey())
            .modelName(connection.model().model())
            .timeout(deriveTimeoutSetting(connection.timeouts()));

    Optional.ofNullable(connection.authentication().organizationId())
        .ifPresent(builder::organizationId);
    Optional.ofNullable(connection.authentication().projectId()).ifPresent(builder::projectId);

    final var modelParameters = connection.model().parameters();
    if (modelParameters != null) {
      final var requestParametersBuilder = OpenAiChatRequestParameters.builder();
      Optional.ofNullable(modelParameters.maxCompletionTokens())
          .ifPresent(requestParametersBuilder::maxCompletionTokens);
      Optional.ofNullable(modelParameters.temperature())
          .ifPresent(requestParametersBuilder::temperature);
      Optional.ofNullable(modelParameters.topP()).ifPresent(requestParametersBuilder::topP);

      builder.defaultRequestParameters(requestParametersBuilder.build());
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
