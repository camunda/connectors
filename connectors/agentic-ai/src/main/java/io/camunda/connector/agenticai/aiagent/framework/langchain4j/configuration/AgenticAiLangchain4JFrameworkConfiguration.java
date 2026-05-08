/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApiFactory;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.ChatMessageConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.ChatMessageConverterImpl;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.ContentConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.ContentConverterImpl;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.Langchain4JChatModelApiFactory;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.document.DocumentToContentConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.document.DocumentToContentConverterImpl;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.jsonschema.JsonSchemaConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.ChatModelProvider;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool.ToolCallConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool.ToolCallConverterImpl;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool.ToolSpecificationConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool.ToolSpecificationConverterImpl;
import io.camunda.connector.agenticai.aiagent.model.request.provider.BedrockProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.GoogleGenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration;
import io.camunda.connector.runtime.annotation.ConnectorsObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@ConditionalOnProperty(
    value = "camunda.connector.agenticai.framework",
    havingValue = "langchain4j",
    matchIfMissing = true)
@Import(AgenticAiLangchain4JChatModelConfiguration.class)
public class AgenticAiLangchain4JFrameworkConfiguration {

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
      @ConnectorsObjectMapper ObjectMapper objectMapper) {
    return new ToolCallConverterImpl(objectMapper);
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
      ContentConverter contentConverter, ToolCallConverter toolCallConverter) {
    return new ChatMessageConverterImpl(contentConverter, toolCallConverter);
  }

  @Bean
  @ConditionalOnMissingBean(name = "langchain4JBedrockChatModelApiFactory")
  public ChatModelApiFactory<BedrockProviderConfiguration> langchain4JBedrockChatModelApiFactory(
      ChatModelProvider<BedrockProviderConfiguration> provider,
      ChatMessageConverter chatMessageConverter,
      ToolSpecificationConverter toolSpecificationConverter,
      JsonSchemaConverter jsonSchemaConverter) {
    return new Langchain4JChatModelApiFactory<>(
        BedrockProviderConfiguration.BEDROCK_ID,
        BedrockProviderConfiguration.class,
        provider,
        chatMessageConverter,
        toolSpecificationConverter,
        jsonSchemaConverter);
  }

  @Bean
  @ConditionalOnMissingBean(
      name = {"langchain4JAzureOpenAiChatModelApiFactory", "langchain4JOpenAiChatModelApiFactory"})
  public ChatModelApiFactory<OpenAiProviderConfiguration> langchain4JAzureOpenAiChatModelApiFactory(
      ChatModelProvider<OpenAiProviderConfiguration> provider,
      ChatMessageConverter chatMessageConverter,
      ToolSpecificationConverter toolSpecificationConverter,
      JsonSchemaConverter jsonSchemaConverter) {
    return new Langchain4JChatModelApiFactory<>(
        OpenAiProviderConfiguration.OPENAI_ID,
        OpenAiProviderConfiguration.class,
        provider,
        chatMessageConverter,
        toolSpecificationConverter,
        jsonSchemaConverter);
  }

  @Bean
  @ConditionalOnMissingBean(name = "langchain4JGoogleVertexAiChatModelApiFactory")
  public ChatModelApiFactory<GoogleGenAiProviderConfiguration>
      langchain4JGoogleVertexAiChatModelApiFactory(
          ChatModelProvider<GoogleGenAiProviderConfiguration> provider,
          ChatMessageConverter chatMessageConverter,
          ToolSpecificationConverter toolSpecificationConverter,
          JsonSchemaConverter jsonSchemaConverter) {
    return new Langchain4JChatModelApiFactory<>(
        GoogleGenAiProviderConfiguration.GOOGLE_GENAI_ID,
        GoogleGenAiProviderConfiguration.class,
        provider,
        chatMessageConverter,
        toolSpecificationConverter,
        jsonSchemaConverter);
  }
}
