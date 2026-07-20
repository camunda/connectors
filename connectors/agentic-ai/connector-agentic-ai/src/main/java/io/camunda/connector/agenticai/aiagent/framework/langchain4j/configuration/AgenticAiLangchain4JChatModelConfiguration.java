/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j.configuration;

import io.camunda.connector.agenticai.aiagent.framework.langchain4j.ChatMessageConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.ChatModelHttpProxySupport;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.Langchain4JChatModelApi;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.jsonschema.JsonSchemaConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.Langchain4JAnthropicChatModelApiFactory;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.Langchain4JAzureOpenAiChatModelApiFactory;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.Langchain4JBedrockChatModelApiFactory;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.Langchain4JGoogleVertexAiChatModelApiFactory;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.Langchain4JOpenAiChatModelApiFactory;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.Langchain4JOpenAiCompatibleChatModelApiFactory;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool.ToolSpecificationConverter;
import io.camunda.connector.agenticai.aiagent.framework.transport.HttpTransportSupport;
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
  public Langchain4JAnthropicChatModelApiFactory aiAgentLangchain4JAnthropicChatModelApiFactory(
      AgenticAiConnectorsConfigurationProperties config,
      ChatModelHttpProxySupport chatModelHttpProxySupport,
      ChatMessageConverter chatMessageConverter,
      ToolSpecificationConverter toolSpecificationConverter,
      JsonSchemaConverter jsonSchemaConverter) {
    return new Langchain4JAnthropicChatModelApiFactory(
        config.aiagent().chatModel(),
        chatModelHttpProxySupport,
        chatMessageConverter,
        toolSpecificationConverter,
        jsonSchemaConverter,
        Langchain4JChatModelApi.DEFAULT_CAPABILITIES);
  }

  @Bean
  @ConditionalOnMissingBean
  public Langchain4JBedrockChatModelApiFactory aiAgentLangchain4JBedrockChatModelApiFactory(
      AgenticAiConnectorsConfigurationProperties config,
      ChatModelHttpProxySupport chatModelHttpProxySupport,
      ChatMessageConverter chatMessageConverter,
      ToolSpecificationConverter toolSpecificationConverter,
      JsonSchemaConverter jsonSchemaConverter) {
    return new Langchain4JBedrockChatModelApiFactory(
        config.aiagent().chatModel(),
        chatModelHttpProxySupport,
        chatMessageConverter,
        toolSpecificationConverter,
        jsonSchemaConverter,
        Langchain4JChatModelApi.DEFAULT_CAPABILITIES);
  }

  @Bean
  @ConditionalOnMissingBean
  public Langchain4JAzureOpenAiChatModelApiFactory aiAgentLangchain4JAzureOpenAiChatModelApiFactory(
      AgenticAiConnectorsConfigurationProperties config,
      ChatModelHttpProxySupport chatModelHttpProxySupport,
      ChatMessageConverter chatMessageConverter,
      ToolSpecificationConverter toolSpecificationConverter,
      JsonSchemaConverter jsonSchemaConverter) {
    return new Langchain4JAzureOpenAiChatModelApiFactory(
        config.aiagent().chatModel(),
        chatModelHttpProxySupport,
        chatMessageConverter,
        toolSpecificationConverter,
        jsonSchemaConverter,
        Langchain4JChatModelApi.DEFAULT_CAPABILITIES);
  }

  @Bean
  @ConditionalOnMissingBean
  public Langchain4JGoogleVertexAiChatModelApiFactory
      aiAgentLangchain4JGoogleVertexAiChatModelApiFactory(
          ChatMessageConverter chatMessageConverter,
          ToolSpecificationConverter toolSpecificationConverter,
          JsonSchemaConverter jsonSchemaConverter) {
    return new Langchain4JGoogleVertexAiChatModelApiFactory(
        chatMessageConverter,
        toolSpecificationConverter,
        jsonSchemaConverter,
        Langchain4JChatModelApi.DEFAULT_CAPABILITIES);
  }

  @Bean
  @ConditionalOnMissingBean
  public Langchain4JOpenAiChatModelApiFactory aiAgentLangchain4JOpenAiChatModelApiFactory(
      AgenticAiConnectorsConfigurationProperties config,
      ChatModelHttpProxySupport chatModelHttpProxySupport,
      ChatMessageConverter chatMessageConverter,
      ToolSpecificationConverter toolSpecificationConverter,
      JsonSchemaConverter jsonSchemaConverter) {
    return new Langchain4JOpenAiChatModelApiFactory(
        config.aiagent().chatModel(),
        chatModelHttpProxySupport,
        chatMessageConverter,
        toolSpecificationConverter,
        jsonSchemaConverter,
        Langchain4JChatModelApi.DEFAULT_CAPABILITIES);
  }

  @Bean
  @ConditionalOnMissingBean
  public Langchain4JOpenAiCompatibleChatModelApiFactory
      aiAgentLangchain4JOpenAiCompatibleChatModelApiFactory(
          AgenticAiConnectorsConfigurationProperties config,
          ChatModelHttpProxySupport chatModelHttpProxySupport,
          ChatMessageConverter chatMessageConverter,
          ToolSpecificationConverter toolSpecificationConverter,
          JsonSchemaConverter jsonSchemaConverter) {
    return new Langchain4JOpenAiCompatibleChatModelApiFactory(
        config.aiagent().chatModel(),
        chatModelHttpProxySupport,
        chatMessageConverter,
        toolSpecificationConverter,
        jsonSchemaConverter,
        Langchain4JChatModelApi.DEFAULT_CAPABILITIES);
  }
}
