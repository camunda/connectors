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
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiCompatibleProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiCompatibleProviderConfiguration.OpenAiCompatibleAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.ProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.TimeoutConfiguration;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties;
import java.time.Duration;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenAiCompatibleChatModelProvider implements ChatModelProvider {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(OpenAiCompatibleChatModelProvider.class);

  private final AgenticAiConnectorsConfigurationProperties.ChatModelProperties chatModelProperties;

  public OpenAiCompatibleChatModelProvider(
      AgenticAiConnectorsConfigurationProperties agenticAiConnectorsConfigurationProperties) {
    this.chatModelProperties = agenticAiConnectorsConfigurationProperties.aiagent().chatModel();
  }

  @Override
  public String getProviderType() {
    return OpenAiCompatibleProviderConfiguration.TYPE;
  }

  @Override
  public boolean supports(ProviderConfiguration providerConfiguration) {
    return providerConfiguration instanceof OpenAiCompatibleProviderConfiguration;
  }

  @Override
  public ChatModel createChatModel(ProviderConfiguration providerConfiguration) {
    if (!(providerConfiguration
        instanceof OpenAiCompatibleProviderConfiguration openaiCompatible)) {
      throw new IllegalArgumentException(
          "Expected OpenAiCompatibleProviderConfiguration but got "
              + providerConfiguration.getClass().getSimpleName());
    }

    final var connection = openaiCompatible.openaiCompatible();

    final var builder =
        OpenAiChatModel.builder()
            .modelName(connection.model().model())
            .baseUrl(connection.endpoint())
            .timeout(deriveTimeoutSetting(connection.timeouts()));

    Optional.ofNullable(connection.authentication())
        .map(OpenAiCompatibleAuthentication::apiKey)
        .filter(StringUtils::isNotBlank)
        .ifPresent(
            apiKey -> {
              builder.apiKey(apiKey);
              if (connection.headers() != null) {
                if (connection.headers().keySet().stream()
                    .anyMatch("Authorization"::equalsIgnoreCase)) {
                  LOGGER.warn(
                      "Both API key and Authorization header are set. The API key will be ignored.");
                  builder.apiKey(null);
                }
              }
            });
    Optional.ofNullable(connection.headers()).ifPresent(builder::customHeaders);
    Optional.ofNullable(connection.queryParameters()).ifPresent(builder::customQueryParams);

    final var modelParameters = connection.model().parameters();
    if (modelParameters != null) {
      final var requestParametersBuilder = OpenAiChatRequestParameters.builder();
      Optional.ofNullable(modelParameters.maxCompletionTokens())
          .ifPresent(requestParametersBuilder::maxCompletionTokens);
      Optional.ofNullable(modelParameters.temperature())
          .ifPresent(requestParametersBuilder::temperature);
      Optional.ofNullable(modelParameters.topP()).ifPresent(requestParametersBuilder::topP);
      Optional.ofNullable(modelParameters.customParameters())
          .ifPresent(requestParametersBuilder::customParameters);

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
