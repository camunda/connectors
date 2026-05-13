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
import io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilitiesResolver;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties;
import io.camunda.connector.runtime.annotation.ConnectorsObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the native OpenAI factory under the same Spring bean name as the LangChain4j bridge
 * factory ({@code langchain4JOpenAiChatModelApiFactory}). The bridge configuration uses
 * {@code @ConditionalOnMissingBean(name = ...)}, so this native bean takes over whenever the OpenAI
 * SDK is on the classpath.
 */
@Configuration
@ConditionalOnClass(OpenAIClient.class)
public class OpenAiChatModelApiConfiguration {

  @Bean(name = "langchain4JOpenAiChatModelApiFactory")
  @ConditionalOnMissingBean(name = "langchain4JOpenAiChatModelApiFactory")
  public ChatModelApiFactory<OpenAiProviderConfiguration> openAiChatModelApiFactory(
      @ConnectorsObjectMapper ObjectMapper objectMapper,
      ModelCapabilitiesResolver capabilitiesResolver,
      AgenticAiConnectorsConfigurationProperties properties) {
    return new OpenAiChatModelApiFactory(
        objectMapper,
        capabilitiesResolver,
        properties.aiagent().chatModel().api().defaultTimeout());
  }
}
