/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider;

import static io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.ChatModelProviderSupport.deriveTimeoutSetting;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.ChatModelHttpProxySupport;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.ChatModelProperties;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AnthropicChatModelProvider
    implements ChatModelProvider<AnthropicProviderConfiguration> {

  private static final Logger LOGGER = LoggerFactory.getLogger(AnthropicChatModelProvider.class);

  private final ChatModelProperties config;
  private final ChatModelHttpProxySupport proxySupport;

  public AnthropicChatModelProvider(
      ChatModelProperties config, ChatModelHttpProxySupport proxySupport) {
    this.config = config;
    this.proxySupport = proxySupport;
  }

  @Override
  public String type() {
    return AnthropicProviderConfiguration.ANTHROPIC_ID;
  }

  @Override
  public ChatModel createChatModel(AnthropicProviderConfiguration anthropic) {
    final var connection = anthropic.anthropic();

    final var builder =
        AnthropicChatModel.builder()
            .apiKey(connection.authentication().apiKey())
            .modelName(connection.model().model())
            .timeout(
                deriveTimeoutSetting("Anthropic model call", config, connection.timeouts(), LOGGER))
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
