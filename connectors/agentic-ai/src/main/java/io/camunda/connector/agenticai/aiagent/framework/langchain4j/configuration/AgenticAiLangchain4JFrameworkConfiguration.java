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
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.Langchain4JAiFrameworkAdapter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.document.DocumentToContentConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.document.DocumentToContentConverterImpl;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool.ToolCallConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool.ToolCallConverterImpl;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool.ToolSpecificationConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool.ToolSpecificationConverterImpl;
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
  public ChatModelFactory langchain4JChatModelFactory() {
    return new ChatModelFactoryImpl();
  }

  @Bean
  @ConditionalOnMissingBean
  public DocumentToContentConverter langchain4JDocumentToContentConverter() {
    return new DocumentToContentConverterImpl();
  }

  @Bean
  @ConditionalOnMissingBean
  public ToolCallConverter langchain4JToolCallConverter(
      ObjectMapper objectMapper, DocumentToContentConverter documentToContentConverter) {
    return new ToolCallConverterImpl(objectMapper, documentToContentConverter);
  }

  @Bean
  @ConditionalOnMissingBean
  public ToolSpecificationConverter langchain4JToolSpecificationConverter() {
    return new ToolSpecificationConverterImpl();
  }

  @Bean
  @ConditionalOnMissingBean
  public ChatMessageConverter langchain4JChatMessageConverter(
      ToolCallConverter toolCallConverter,
      DocumentToContentConverter documentToContentConverter,
      ObjectMapper objectMapper) {
    return new ChatMessageConverterImpl(
        toolCallConverter, documentToContentConverter, objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public Langchain4JAiFrameworkAdapter langchain4JAiFrameworkAdapter(
      ChatModelFactory chatModelFactory,
      ChatMessageConverter chatMessageConverter,
      ToolSpecificationConverter toolSpecificationConverter) {
    return new Langchain4JAiFrameworkAdapter(
        chatModelFactory, chatMessageConverter, toolSpecificationConverter);
  }
}
