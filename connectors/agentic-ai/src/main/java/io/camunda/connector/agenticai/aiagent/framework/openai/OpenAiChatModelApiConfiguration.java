/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApiFactory;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiCompatibleProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties;
import io.camunda.connector.runtime.annotation.ConnectorsObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the native OpenAI factories under the same Spring bean names as the LangChain4j bridge
 * factories ({@code langchain4JOpenAiChatModelApiFactory} and {@code
 * langchain4JOpenAiCompatibleChatModelApiFactory}). The bridge configuration uses
 * {@code @ConditionalOnMissingBean(name = ...)}, so these native beans take over whenever the
 * OpenAI SDK is on the classpath. Azure OpenAI stays on the bridge for now (Phase G).
 */
@Configuration
@ConditionalOnClass(OpenAIClient.class)
public class OpenAiChatModelApiConfiguration {

  @Bean(name = "langchain4JOpenAiChatModelApiFactory")
  @ConditionalOnMissingBean(name = "langchain4JOpenAiChatModelApiFactory")
  public ChatModelApiFactory<OpenAiProviderConfiguration> openAiChatModelApiFactory(
      @ConnectorsObjectMapper ObjectMapper objectMapper,
      AgenticAiConnectorsConfigurationProperties properties) {
    return new OpenAiChatModelApiFactory(
        objectMapper, properties.aiagent().chatModel().api().defaultTimeout());
  }

  @Bean(name = "langchain4JOpenAiCompatibleChatModelApiFactory")
  @ConditionalOnMissingBean(name = "langchain4JOpenAiCompatibleChatModelApiFactory")
  public ChatModelApiFactory<OpenAiCompatibleProviderConfiguration>
      openAiCompatibleChatModelApiFactory(
          @ConnectorsObjectMapper ObjectMapper objectMapper,
          AgenticAiConnectorsConfigurationProperties properties) {
    return new OpenAiCompatibleChatModelApiFactory(
        objectMapper, properties.aiagent().chatModel().api().defaultTimeout());
  }
}
