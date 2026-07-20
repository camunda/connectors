/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j.configuration;

import io.camunda.connector.agenticai.aiagent.framework.langchain4j.ChatModelHttpProxySupport;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.AnthropicChatModelProvider;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.AzureOpenAiChatModelProvider;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.BedrockChatModelProvider;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.ChatModelProvider;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.GoogleVertexAiChatModelProvider;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.OpenAiChatModelProvider;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.OpenAiCompatibleChatModelProvider;
import io.camunda.connector.agenticai.aiagent.framework.transport.HttpTransportSupport;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureOpenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.BedrockProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.GoogleVertexAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiCompatibleProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgenticAiLangchain4JChatModelConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public ChatModelHttpProxySupport aiAgentLangchain4JChatModelHttpProxySupport(
      HttpTransportSupport httpTransportSupport) {
    return new ChatModelHttpProxySupport(httpTransportSupport);
  }

  @Bean
  @ConditionalOnMissingBean
  public ChatModelProvider<AnthropicProviderConfiguration>
      aiAgentLangchain4JAnthropicChatModelProvider(
          AgenticAiConnectorsConfigurationProperties config,
          ChatModelHttpProxySupport chatModelHttpProxySupport) {
    return new AnthropicChatModelProvider(config.aiagent().chatModel(), chatModelHttpProxySupport);
  }

  @Bean
  @ConditionalOnMissingBean
  public ChatModelProvider<AzureOpenAiProviderConfiguration>
      aiAgentLangchain4JAzureOpenAiChatModelProvider(
          AgenticAiConnectorsConfigurationProperties config,
          ChatModelHttpProxySupport chatModelHttpProxySupport) {
    return new AzureOpenAiChatModelProvider(
        config.aiagent().chatModel(), chatModelHttpProxySupport);
  }

  @Bean
  @ConditionalOnMissingBean
  public ChatModelProvider<BedrockProviderConfiguration> aiAgentLangchain4JBedrockChatModelProvider(
      AgenticAiConnectorsConfigurationProperties config,
      ChatModelHttpProxySupport chatModelHttpProxySupport) {
    return new BedrockChatModelProvider(config.aiagent().chatModel(), chatModelHttpProxySupport);
  }

  @Bean
  @ConditionalOnMissingBean
  public ChatModelProvider<GoogleVertexAiProviderConfiguration>
      aiAgentLangchain4JGoogleVertexAiChatModelProvider() {
    return new GoogleVertexAiChatModelProvider();
  }

  @Bean
  @ConditionalOnMissingBean
  public ChatModelProvider<OpenAiProviderConfiguration> aiAgentLangchain4JOpenAiChatModelProvider(
      AgenticAiConnectorsConfigurationProperties config,
      ChatModelHttpProxySupport chatModelHttpProxySupport) {
    return new OpenAiChatModelProvider(config.aiagent().chatModel(), chatModelHttpProxySupport);
  }

  @Bean
  @ConditionalOnMissingBean
  public ChatModelProvider<OpenAiCompatibleProviderConfiguration>
      aiAgentLangchain4JOpenAiCompatibleChatModelProvider(
          AgenticAiConnectorsConfigurationProperties config,
          ChatModelHttpProxySupport chatModelHttpProxySupport) {
    return new OpenAiCompatibleChatModelProvider(
        config.aiagent().chatModel(), chatModelHttpProxySupport);
  }
}
