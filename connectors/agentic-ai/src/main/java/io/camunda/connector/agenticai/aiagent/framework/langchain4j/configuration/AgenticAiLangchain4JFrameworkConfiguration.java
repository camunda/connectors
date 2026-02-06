/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.ChatMessageConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.ChatMessageConverterImpl;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.ChatModelFactory;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.ChatModelFactoryImpl;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.ContentConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.ContentConverterImpl;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.Langchain4JAiFrameworkAdapter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.document.DocumentToContentConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.document.DocumentToContentConverterImpl;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.jsonschema.JsonSchemaConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.AnthropicChatModelProvider;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.AzureOpenAiChatModelProvider;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.BedrockChatModelProvider;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.ChatModelProvider;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.ChatModelProviderRegistry;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.GoogleVertexAiChatModelProvider;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.OpenAiChatModelProvider;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.OpenAiCompatibleChatModelProvider;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool.ToolCallConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool.ToolCallConverterImpl;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool.ToolSpecificationConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool.ToolSpecificationConverterImpl;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties;
import io.camunda.connector.runtime.annotation.ConnectorsObjectMapper;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(
    value = "camunda.connector.agenticai.framework",
    havingValue = "langchain4j",
    matchIfMissing = true)
public class AgenticAiLangchain4JFrameworkConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public AnthropicChatModelProvider langchain4JAnthropicChatModelProvider(
      AgenticAiConnectorsConfigurationProperties agenticAiConnectorsConfigurationProperties) {
    return new AnthropicChatModelProvider(agenticAiConnectorsConfigurationProperties);
  }

  @Bean
  @ConditionalOnMissingBean
  public AzureOpenAiChatModelProvider langchain4JAzureOpenAiChatModelProvider(
      AgenticAiConnectorsConfigurationProperties agenticAiConnectorsConfigurationProperties) {
    return new AzureOpenAiChatModelProvider(agenticAiConnectorsConfigurationProperties);
  }

  @Bean
  @ConditionalOnMissingBean
  public BedrockChatModelProvider langchain4JBedrockChatModelProvider(
      AgenticAiConnectorsConfigurationProperties agenticAiConnectorsConfigurationProperties) {
    return new BedrockChatModelProvider(agenticAiConnectorsConfigurationProperties);
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
      AgenticAiConnectorsConfigurationProperties agenticAiConnectorsConfigurationProperties) {
    return new OpenAiChatModelProvider(agenticAiConnectorsConfigurationProperties);
  }

  @Bean
  @ConditionalOnMissingBean
  public OpenAiCompatibleChatModelProvider langchain4JOpenAiCompatibleChatModelProvider(
      AgenticAiConnectorsConfigurationProperties agenticAiConnectorsConfigurationProperties) {
    return new OpenAiCompatibleChatModelProvider(agenticAiConnectorsConfigurationProperties);
  }

  @Bean
  @ConditionalOnMissingBean
  public ChatModelProviderRegistry langchain4JChatModelProviderRegistry(
      List<ChatModelProvider> chatModelProviders) {
    return new ChatModelProviderRegistry(chatModelProviders);
  }

  @Bean
  @ConditionalOnMissingBean
  public ChatModelFactory langchain4JChatModelFactory(
      ChatModelProviderRegistry chatModelProviderRegistry) {
    return new ChatModelFactoryImpl(chatModelProviderRegistry);
  }

  @Bean
  @ConditionalOnMissingBean
  public DocumentToContentConverter langchain4JDocumentToContentConverter() {
    return new DocumentToContentConverterImpl();
  }

  @Bean
  @ConditionalOnMissingBean
  public ContentConverter langchain4JContentConverter(
      @ConnectorsObjectMapper ObjectMapper objectMapper,
      DocumentToContentConverter documentToContentConverter) {
    return new ContentConverterImpl(objectMapper, documentToContentConverter);
  }

  @Bean
  @ConditionalOnMissingBean
  public ToolCallConverter langchain4JToolCallConverter(
      @ConnectorsObjectMapper ObjectMapper objectMapper, ContentConverter contentConverter) {
    return new ToolCallConverterImpl(objectMapper, contentConverter);
  }

  @Bean
  @ConditionalOnMissingBean
  public JsonSchemaConverter langchain4JJsonSchemaConverter(
      @ConnectorsObjectMapper ObjectMapper objectMapper) {
    return new JsonSchemaConverter(objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public ToolSpecificationConverter langchain4JToolSpecificationConverter(
      JsonSchemaConverter jsonSchemaConverter) {
    return new ToolSpecificationConverterImpl(jsonSchemaConverter);
  }

  @Bean
  @ConditionalOnMissingBean
  public ChatMessageConverter langchain4JChatMessageConverter(
      ContentConverter contentConverter,
      ToolCallConverter toolCallConverter,
      @ConnectorsObjectMapper ObjectMapper objectMapper) {
    return new ChatMessageConverterImpl(contentConverter, toolCallConverter, objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public Langchain4JAiFrameworkAdapter langchain4JAiFrameworkAdapter(
      ChatModelFactory chatModelFactory,
      ChatMessageConverter chatMessageConverter,
      ToolSpecificationConverter toolSpecificationConverter,
      JsonSchemaConverter jsonSchemaConverter) {
    return new Langchain4JAiFrameworkAdapter(
        chatModelFactory, chatMessageConverter, toolSpecificationConverter, jsonSchemaConverter);
  }
}
