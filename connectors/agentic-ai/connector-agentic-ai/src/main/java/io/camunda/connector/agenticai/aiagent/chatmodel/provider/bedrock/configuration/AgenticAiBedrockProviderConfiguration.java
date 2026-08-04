/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.bedrock.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.bedrock.BedrockChatModelApiFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.bedrock.BedrockConverseContentConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.bedrock.BedrockConverseRequestConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.bedrock.BedrockConverseResponseConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.ChatModelHttpProxySupport;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties;
import io.camunda.connector.runtime.annotation.ConnectorsObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the {@link BedrockChatModelApiFactory} as a {@code ChatModelFactory} bean so it is
 * picked up by {@code aiAgentChatModelRegistry(List<ChatModelFactory>)} and resolved for the
 * configurations it supports. A dedicated, imported configuration per native provider, mirroring
 * {@code AgenticAiAnthropicProviderConfiguration}'s pattern for the LangChain4J-routed providers'
 * sibling native provider.
 */
@Configuration
public class AgenticAiBedrockProviderConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public BedrockChatModelApiFactory aiAgentBedrockChatModelApiFactory(
      AgenticAiConnectorsConfigurationProperties configuration,
      ChatModelHttpProxySupport httpProxySupport,
      @ConnectorsObjectMapper ObjectMapper objectMapper) {
    final var contentConverter = new BedrockConverseContentConverter(objectMapper);
    final var requestConverter =
        new BedrockConverseRequestConverter(contentConverter, objectMapper);
    final var responseConverter = new BedrockConverseResponseConverter();
    return new BedrockChatModelApiFactory(
        configuration.aiagent().chatModel(),
        httpProxySupport,
        requestConverter,
        responseConverter,
        objectMapper);
  }
}
