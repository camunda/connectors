/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent;

import io.camunda.connector.agenticai.aiagent.configuration.AgenticAiConnectorsConfigurationProperties.ChatModelProperties;
import io.camunda.connector.agenticai.aiagent.configuration.AgenticAiConnectorsConfigurationProperties.ChatModelProperties.ApiProperties;
import io.camunda.connector.agenticai.aiagent.model.chatmodel.ChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.request.V1ToV2ProviderConfigurationMapperImpl;
import io.camunda.connector.agenticai.aiagent.request.v2.ProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.translation.ChatModelConfigurationTranslator;
import java.time.Duration;

/**
 * Translates provider configurations of requests like the connector does, so that provider tests
 * start from the request records and exercise the domain configuration of the provider.
 */
public final class ChatModelTranslationFixture {

  public static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(3);

  private static final ChatModelConfigurationTranslator TRANSLATOR =
      new ChatModelConfigurationTranslator(
          new V1ToV2ProviderConfigurationMapperImpl(
              new ChatModelProperties(new ApiProperties(DEFAULT_TIMEOUT), null)),
          DEFAULT_TIMEOUT);

  private ChatModelTranslationFixture() {}

  public static ChatModelConfiguration translate(ProviderConfiguration providerConfiguration) {
    return TRANSLATOR.translate(providerConfiguration);
  }

  @SuppressWarnings("unchecked")
  public static <T extends ChatModelConfiguration> T translate(
      ProviderConfiguration providerConfiguration, Class<T> type) {
    return type.cast(TRANSLATOR.translate(providerConfiguration));
  }
}
