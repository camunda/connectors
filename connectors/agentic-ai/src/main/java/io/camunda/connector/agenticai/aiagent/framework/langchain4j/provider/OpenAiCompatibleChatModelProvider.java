/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider;

import static io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.ChatModelProviderSupport.deriveTimeoutSetting;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.ChatModelHttpProxySupport;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiCompatibleProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiCompatibleProviderConfiguration.OpenAiCompatibleAuthentication;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.ChatModelProperties;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenAiCompatibleChatModelProvider
    implements ChatModelProvider<OpenAiCompatibleProviderConfiguration> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(OpenAiCompatibleChatModelProvider.class);

  private final ChatModelProperties config;
  private final ChatModelHttpProxySupport proxySupport;

  public OpenAiCompatibleChatModelProvider(
      ChatModelProperties config, ChatModelHttpProxySupport proxySupport) {
    this.config = config;
    this.proxySupport = proxySupport;
  }

  @Override
  public String type() {
    return OpenAiCompatibleProviderConfiguration.OPENAI_COMPATIBLE_ID;
  }

  @Override
  public ChatModel createChatModel(OpenAiCompatibleProviderConfiguration openaiCompatible) {
    final var connection = openaiCompatible.openaiCompatible();

    final var builder =
        OpenAiChatModel.builder()
            .modelName(connection.model().model())
            .baseUrl(connection.endpoint())
            .timeout(
                deriveTimeoutSetting(
                    "OpenAI compatible model call", config, connection.timeouts(), LOGGER))
            .httpClientBuilder(proxySupport.createJdkHttpClientBuilder());

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
}
