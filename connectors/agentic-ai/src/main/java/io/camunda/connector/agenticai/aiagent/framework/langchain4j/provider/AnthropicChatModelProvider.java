/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.ChatModelHttpProxySupport;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.ProviderConfiguration;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties;
import java.util.Optional;

public class AnthropicChatModelProvider extends AbstractChatModelProvider {

  private final ChatModelHttpProxySupport proxySupport;

  public AnthropicChatModelProvider(
      AgenticAiConnectorsConfigurationProperties agenticAiConnectorsConfigurationProperties,
      ChatModelHttpProxySupport proxySupport) {
    super(agenticAiConnectorsConfigurationProperties);
    this.proxySupport = proxySupport;
  }

  @Override
  public String type() {
    return AnthropicProviderConfiguration.ANTHROPIC_ID;
  }

  @Override
  public ChatModel createChatModel(ProviderConfiguration providerConfiguration) {
    if (!(providerConfiguration instanceof AnthropicProviderConfiguration anthropic)) {
      throw new IllegalArgumentException(
          "Expected AnthropicProviderConfiguration but got "
              + providerConfiguration.getClass().getSimpleName());
    }

    final var connection = anthropic.anthropic();

    final var builder =
        AnthropicChatModel.builder()
            .apiKey(connection.authentication().apiKey())
            .modelName(connection.model().model())
            .timeout(deriveTimeoutSetting(connection.timeouts()))
            .httpClientBuilder(proxySupport.createJdkHttpClientBuilder());

    Optional.ofNullable(connection.endpoint()).ifPresent(builder::baseUrl);

    final var modelParameters = connection.model().parameters();
    if (modelParameters != null) {
      Optional.ofNullable(modelParameters.maxTokens()).ifPresent(builder::maxTokens);
      Optional.ofNullable(modelParameters.temperature()).ifPresent(builder::temperature);
      Optional.ofNullable(modelParameters.topP()).ifPresent(builder::topP);
      Optional.ofNullable(modelParameters.topK()).ifPresent(builder::topK);
    }

    return builder.build();
  }
}
