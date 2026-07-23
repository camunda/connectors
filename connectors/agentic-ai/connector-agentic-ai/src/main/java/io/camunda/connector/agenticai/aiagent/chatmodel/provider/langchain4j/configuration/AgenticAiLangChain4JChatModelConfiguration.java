/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.configuration;

import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.ChatMessageConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.ChatModelHttpProxySupport;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory.AnthropicChatModelFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory.AzureOpenAiChatModelFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory.BedrockChatModelFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory.GoogleVertexAiChatModelFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory.OpenAiChatModelFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory.OpenAiCompatibleChatModelFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.jsonschema.JsonSchemaConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.tool.ToolSpecificationConverter;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties;
import io.camunda.connector.agenticai.common.AgenticAiHttpProxySupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgenticAiLangChain4JChatModelConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public ChatModelHttpProxySupport langChain4JChatModelHttpProxySupport(
      AgenticAiHttpProxySupport httpProxySupport) {
    return new ChatModelHttpProxySupport(
        httpProxySupport.getProxyConfiguration(),
        httpProxySupport.getJdkHttpClientProxyConfigurator());
  }

  @Bean
  @ConditionalOnMissingBean
  public AnthropicChatModelFactory langChain4JAnthropicChatModelFactory(
      AgenticAiConnectorsConfigurationProperties config,
      ChatModelHttpProxySupport chatModelHttpProxySupport,
      ChatMessageConverter chatMessageConverter,
      ToolSpecificationConverter toolSpecificationConverter,
      JsonSchemaConverter jsonSchemaConverter) {
    return new AnthropicChatModelFactory(
        config.aiagent().chatModel(),
        chatModelHttpProxySupport,
        chatMessageConverter,
        toolSpecificationConverter,
        jsonSchemaConverter);
  }

  @Bean
  @ConditionalOnMissingBean
  public AzureOpenAiChatModelFactory langChain4JAzureOpenAiChatModelFactory(
      AgenticAiConnectorsConfigurationProperties config,
      ChatModelHttpProxySupport chatModelHttpProxySupport,
      ChatMessageConverter chatMessageConverter,
      ToolSpecificationConverter toolSpecificationConverter,
      JsonSchemaConverter jsonSchemaConverter) {
    return new AzureOpenAiChatModelFactory(
        config.aiagent().chatModel(),
        chatModelHttpProxySupport,
        chatMessageConverter,
        toolSpecificationConverter,
        jsonSchemaConverter);
  }

  @Bean
  @ConditionalOnMissingBean
  public BedrockChatModelFactory langChain4JBedrockChatModelFactory(
      AgenticAiConnectorsConfigurationProperties config,
      ChatModelHttpProxySupport chatModelHttpProxySupport,
      ChatMessageConverter chatMessageConverter,
      ToolSpecificationConverter toolSpecificationConverter,
      JsonSchemaConverter jsonSchemaConverter) {
    return new BedrockChatModelFactory(
        config.aiagent().chatModel(),
        chatModelHttpProxySupport,
        chatMessageConverter,
        toolSpecificationConverter,
        jsonSchemaConverter);
  }

  @Bean
  @ConditionalOnMissingBean
  public GoogleVertexAiChatModelFactory langChain4JGoogleVertexAiChatModelFactory(
      ChatMessageConverter chatMessageConverter,
      ToolSpecificationConverter toolSpecificationConverter,
      JsonSchemaConverter jsonSchemaConverter) {
    return new GoogleVertexAiChatModelFactory(
        chatMessageConverter, toolSpecificationConverter, jsonSchemaConverter);
  }

  @Bean
  @ConditionalOnMissingBean
  public OpenAiChatModelFactory langChain4JOpenAiChatModelFactory(
      AgenticAiConnectorsConfigurationProperties config,
      ChatModelHttpProxySupport chatModelHttpProxySupport,
      ChatMessageConverter chatMessageConverter,
      ToolSpecificationConverter toolSpecificationConverter,
      JsonSchemaConverter jsonSchemaConverter) {
    return new OpenAiChatModelFactory(
        config.aiagent().chatModel(),
        chatModelHttpProxySupport,
        chatMessageConverter,
        toolSpecificationConverter,
        jsonSchemaConverter);
  }

  @Bean
  @ConditionalOnMissingBean
  public OpenAiCompatibleChatModelFactory langChain4JOpenAiCompatibleChatModelFactory(
      AgenticAiConnectorsConfigurationProperties config,
      ChatModelHttpProxySupport chatModelHttpProxySupport,
      ChatMessageConverter chatMessageConverter,
      ToolSpecificationConverter toolSpecificationConverter,
      JsonSchemaConverter jsonSchemaConverter) {
    return new OpenAiCompatibleChatModelFactory(
        config.aiagent().chatModel(),
        chatModelHttpProxySupport,
        chatMessageConverter,
        toolSpecificationConverter,
        jsonSchemaConverter);
  }
}
