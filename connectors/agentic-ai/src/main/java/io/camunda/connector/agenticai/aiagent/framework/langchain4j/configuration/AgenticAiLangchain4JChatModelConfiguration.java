/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j.configuration;

import io.camunda.connector.agenticai.aiagent.framework.langchain4j.ChatModelFactory;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.ChatModelFactoryImpl;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.ChatModelHttpProxySupport;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.AnthropicChatModelProvider;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.AzureOpenAiChatModelProvider;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.BedrockChatModelProvider;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.ChatModelProvider;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.ChatModelProviderRegistry;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.GoogleVertexAiChatModelProvider;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.OpenAiChatModelProvider;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.OpenAiCompatibleChatModelProvider;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties;
import io.camunda.connector.agenticai.common.AgenticAiHttpProxySupport;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgenticAiLangchain4JChatModelConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public ChatModelHttpProxySupport langchain4JChatModelHttpProxySupport(
      AgenticAiHttpProxySupport httpProxySupport) {
    return new ChatModelHttpProxySupport(
        httpProxySupport.getProxyConfiguration(),
        httpProxySupport.getJdkHttpClientProxyConfigurator());
  }

  @Bean
  @ConditionalOnMissingBean
  public AnthropicChatModelProvider langchain4JAnthropicChatModelProvider(
      AgenticAiConnectorsConfigurationProperties agenticAiConnectorsConfigurationProperties,
      ChatModelHttpProxySupport chatModelHttpProxySupport) {
    return new AnthropicChatModelProvider(
        agenticAiConnectorsConfigurationProperties, chatModelHttpProxySupport);
  }

  @Bean
  @ConditionalOnMissingBean
  public AzureOpenAiChatModelProvider langchain4JAzureOpenAiChatModelProvider(
      AgenticAiConnectorsConfigurationProperties agenticAiConnectorsConfigurationProperties,
      ChatModelHttpProxySupport chatModelHttpProxySupport) {
    return new AzureOpenAiChatModelProvider(
        agenticAiConnectorsConfigurationProperties, chatModelHttpProxySupport);
  }

  @Bean
  @ConditionalOnMissingBean
  public BedrockChatModelProvider langchain4JBedrockChatModelProvider(
      AgenticAiConnectorsConfigurationProperties agenticAiConnectorsConfigurationProperties,
      ChatModelHttpProxySupport chatModelHttpProxySupport) {
    return new BedrockChatModelProvider(
        agenticAiConnectorsConfigurationProperties, chatModelHttpProxySupport);
  }

  @Bean
  @ConditionalOnMissingBean
  public GoogleVertexAiChatModelProvider langchain4JGoogleVertexAiChatModelProvider(
      AgenticAiConnectorsConfigurationProperties agenticAiConnectorsConfigurationProperties) {
    return new GoogleVertexAiChatModelProvider(agenticAiConnectorsConfigurationProperties);
  }

  @Bean
  @ConditionalOnMissingBean
  public OpenAiChatModelProvider langchain4JOpenAiChatModelProvider(
      AgenticAiConnectorsConfigurationProperties agenticAiConnectorsConfigurationProperties,
      ChatModelHttpProxySupport chatModelHttpProxySupport) {
    return new OpenAiChatModelProvider(
        agenticAiConnectorsConfigurationProperties, chatModelHttpProxySupport);
  }

  @Bean
  @ConditionalOnMissingBean
  public OpenAiCompatibleChatModelProvider langchain4JOpenAiCompatibleChatModelProvider(
      AgenticAiConnectorsConfigurationProperties agenticAiConnectorsConfigurationProperties,
      ChatModelHttpProxySupport chatModelHttpProxySupport) {
    return new OpenAiCompatibleChatModelProvider(
        agenticAiConnectorsConfigurationProperties, chatModelHttpProxySupport);
  }

  @Bean
  @ConditionalOnMissingBean
  public ChatModelProviderRegistry langchain4JChatModelProviderRegistry(
      List<ChatModelProvider<?>> chatModelProviders) {
    return new ChatModelProviderRegistry(chatModelProviders);
  }

  @Bean
  @ConditionalOnMissingBean
  public ChatModelFactory langchain4JChatModelFactory(
      ChatModelProviderRegistry chatModelProviderRegistry) {
    return new ChatModelFactoryImpl(chatModelProviderRegistry);
  }
}
