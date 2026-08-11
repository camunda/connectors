/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic.AnthropicChatModelApiFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic.AnthropicContentConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic.AnthropicMessageRequestConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic.AnthropicMessageResponseConverter;
import io.camunda.connector.agenticai.common.AgenticAiHttpProxySupport;
import io.camunda.connector.runtime.annotation.ConnectorsObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the {@link AnthropicChatModelApiFactory} as a {@code ChatModelFactory} bean so it is
 * picked up by {@code aiAgentChatModelRegistry(List<ChatModelFactory>)} and resolved for the
 * configurations it supports. A dedicated, imported configuration per native provider, mirroring
 * {@code AgenticAiLangChain4JChatModelConfiguration}'s pattern for the LangChain4J-routed
 * providers.
 */
@Configuration
public class AgenticAiAnthropicProviderConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public AnthropicChatModelApiFactory aiAgentAnthropicChatModelApiFactory(
      AgenticAiHttpProxySupport httpProxySupport,
      @ConnectorsObjectMapper ObjectMapper objectMapper) {
    final var contentConverter = new AnthropicContentConverter(objectMapper);
    final var requestConverter = new AnthropicMessageRequestConverter(contentConverter);
    final var responseConverter = new AnthropicMessageResponseConverter(objectMapper);
    return new AnthropicChatModelApiFactory(httpProxySupport, requestConverter, responseConverter);
  }
}
