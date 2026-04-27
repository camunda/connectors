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
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.jsonschema.JsonSchemaConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.AnthropicChatModelProvider;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.AzureFoundryChatModelProvider;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.AzureOpenAiChatModelProvider;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.BedrockChatModelProvider;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.ChatModelProvider;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.ChatModelProviderRegistry;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.GoogleVertexAiChatModelProvider;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.OpenAiChatModelProvider;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.OpenAiCompatibleChatModelProvider;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureFoundryProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.BedrockProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.GoogleVertexAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiCompatibleProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties;
import io.camunda.connector.agenticai.azurefoundry.AnthropicOnFoundryClientFactory;
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
  public ChatModelProvider<AnthropicProviderConfiguration> langchain4JAnthropicChatModelProvider(
      AgenticAiConnectorsConfigurationProperties config,
      ChatModelHttpProxySupport chatModelHttpProxySupport) {
    return new AnthropicChatModelProvider(config.aiagent().chatModel(), chatModelHttpProxySupport);
  }

  @Bean
  @ConditionalOnMissingBean
  public AzureOpenAiChatModelProvider langchain4JAzureOpenAiChatModelProvider(
      AgenticAiConnectorsConfigurationProperties config,
      ChatModelHttpProxySupport chatModelHttpProxySupport) {
    return new AzureOpenAiChatModelProvider(
        config.aiagent().chatModel(), chatModelHttpProxySupport);
  }

  @Bean
  @ConditionalOnMissingBean
  public AnthropicOnFoundryClientFactory anthropicOnFoundryClientFactory(
      ChatModelHttpProxySupport chatModelHttpProxySupport,
      JsonSchemaConverter jsonSchemaConverter) {
    return new AnthropicOnFoundryClientFactory(chatModelHttpProxySupport, jsonSchemaConverter);
  }

  @Bean
  @ConditionalOnMissingBean
  public ChatModelProvider<AzureFoundryProviderConfiguration>
      langchain4JAzureFoundryChatModelProvider(
          AgenticAiConnectorsConfigurationProperties config,
          AnthropicOnFoundryClientFactory anthropicOnFoundryClientFactory,
          AzureOpenAiChatModelProvider azureOpenAiChatModelProvider) {
    return new AzureFoundryChatModelProvider(
        config.aiagent().chatModel(),
        anthropicOnFoundryClientFactory,
        azureOpenAiChatModelProvider);
  }

  @Bean
  @ConditionalOnMissingBean
  public ChatModelProvider<BedrockProviderConfiguration> langchain4JBedrockChatModelProvider(
      AgenticAiConnectorsConfigurationProperties config,
      ChatModelHttpProxySupport chatModelHttpProxySupport) {
    return new BedrockChatModelProvider(config.aiagent().chatModel(), chatModelHttpProxySupport);
  }

  @Bean
  @ConditionalOnMissingBean
  public ChatModelProvider<GoogleVertexAiProviderConfiguration>
      langchain4JGoogleVertexAiChatModelProvider() {
    return new GoogleVertexAiChatModelProvider();
  }

  @Bean
  @ConditionalOnMissingBean
  public ChatModelProvider<OpenAiProviderConfiguration> langchain4JOpenAiChatModelProvider(
      AgenticAiConnectorsConfigurationProperties config,
      ChatModelHttpProxySupport chatModelHttpProxySupport) {
    return new OpenAiChatModelProvider(config.aiagent().chatModel(), chatModelHttpProxySupport);
  }

  @Bean
  @ConditionalOnMissingBean
  public ChatModelProvider<OpenAiCompatibleProviderConfiguration>
      langchain4JOpenAiCompatibleChatModelProvider(
          AgenticAiConnectorsConfigurationProperties config,
          ChatModelHttpProxySupport chatModelHttpProxySupport) {
    return new OpenAiCompatibleChatModelProvider(
        config.aiagent().chatModel(), chatModelHttpProxySupport);
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
