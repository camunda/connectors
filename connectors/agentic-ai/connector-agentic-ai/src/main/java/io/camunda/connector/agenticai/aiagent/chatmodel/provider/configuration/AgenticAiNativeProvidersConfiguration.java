/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic.AnthropicChatModelFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic.AnthropicContentConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic.AnthropicMessageRequestConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic.AnthropicMessageResponseConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.OpenAiChatModelFactory;
import io.camunda.connector.agenticai.common.AgenticAiHttpProxySupport;
import io.camunda.connector.runtime.annotation.ConnectorsObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgenticAiNativeProvidersConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public AnthropicChatModelFactory aiAgentAnthropicChatModelFactory(
      AgenticAiHttpProxySupport httpProxySupport,
      @ConnectorsObjectMapper ObjectMapper objectMapper) {
    final var contentConverter = new AnthropicContentConverter(objectMapper);
    final var requestConverter = new AnthropicMessageRequestConverter(contentConverter);
    final var responseConverter = new AnthropicMessageResponseConverter(objectMapper);
    return new AnthropicChatModelFactory(httpProxySupport, requestConverter, responseConverter);
  }

  @Bean
  @ConditionalOnMissingBean
  public OpenAiChatModelFactory aiAgentOpenAiChatModelFactory(
      AgenticAiHttpProxySupport httpProxySupport,
      @ConnectorsObjectMapper ObjectMapper objectMapper) {
    return new OpenAiChatModelFactory(httpProxySupport, objectMapper);
  }
}
