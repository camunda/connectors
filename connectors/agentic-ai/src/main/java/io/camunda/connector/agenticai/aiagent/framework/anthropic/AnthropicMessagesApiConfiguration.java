/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.anthropic;

import com.anthropic.client.AnthropicClient;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApiFactory;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration that registers the native Anthropic Messages factory under the same bean
 * name as the LangChain4j bridge factory ({@code langchain4JAnthropicChatModelApiFactory}). The
 * bridge bean is gated on {@code @ConditionalOnMissingBean(name = ...)} so this native bean wins
 * automatically whenever the SDK is on the classpath.
 */
@Configuration
@ConditionalOnClass(AnthropicClient.class)
public class AnthropicMessagesApiConfiguration {

  @Bean(name = "langchain4JAnthropicChatModelApiFactory")
  @ConditionalOnMissingBean(name = "langchain4JAnthropicChatModelApiFactory")
  public ChatModelApiFactory<AnthropicProviderConfiguration> anthropicMessagesChatModelApiFactory(
      AgenticAiConnectorsConfigurationProperties properties) {
    return new AnthropicMessagesChatModelApiFactory(
        properties.aiagent().chatModel().api().defaultTimeout());
  }
}
