/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.OpenAiChatModelApiFactory;
import io.camunda.connector.agenticai.common.AgenticAiHttpProxySupport;
import io.camunda.connector.runtime.annotation.ConnectorsObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the {@link OpenAiChatModelApiFactory} as a {@code ChatModelFactory} bean so it is
 * picked up by {@code aiAgentChatModelRegistry(List<ChatModelFactory>)} and resolved for the
 * configurations it supports. A dedicated, imported configuration per native provider, mirroring
 * {@code AgenticAiAnthropicProviderConfiguration}'s pattern.
 */
@Configuration
public class AgenticAiOpenAiProviderConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public OpenAiChatModelApiFactory aiAgentOpenAiChatModelApiFactory(
      AgenticAiHttpProxySupport httpProxySupport,
      @ConnectorsObjectMapper ObjectMapper objectMapper) {
    return new OpenAiChatModelApiFactory(httpProxySupport, objectMapper);
  }
}
