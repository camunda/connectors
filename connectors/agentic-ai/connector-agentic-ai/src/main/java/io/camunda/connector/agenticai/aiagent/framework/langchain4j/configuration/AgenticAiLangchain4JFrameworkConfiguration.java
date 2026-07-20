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
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.ContentConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.ContentConverterImpl;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.document.DocumentToContentConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.document.DocumentToContentConverterImpl;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.jsonschema.JsonSchemaConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool.ToolCallConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool.ToolCallConverterImpl;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool.ToolSpecificationConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool.ToolSpecificationConverterImpl;
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
  public DocumentToContentConverter aiAgentLangchain4JDocumentToContentConverter() {
    return new DocumentToContentConverterImpl();
  }

  @Bean
  @ConditionalOnMissingBean
  public ContentConverter aiAgentLangchain4JContentConverter(
      @ConnectorsObjectMapper ObjectMapper objectMapper,
      DocumentToContentConverter documentToContentConverter) {
    return new ContentConverterImpl(objectMapper, documentToContentConverter);
  }

  @Bean
  @ConditionalOnMissingBean
  public ToolCallConverter aiAgentLangchain4JToolCallConverter(
      @ConnectorsObjectMapper ObjectMapper objectMapper) {
    return new ToolCallConverterImpl(objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public JsonSchemaConverter aiAgentLangchain4JJsonSchemaConverter(
      @ConnectorsObjectMapper ObjectMapper objectMapper) {
    return new JsonSchemaConverter(objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public ToolSpecificationConverter aiAgentLangchain4JToolSpecificationConverter(
      JsonSchemaConverter jsonSchemaConverter) {
    return new ToolSpecificationConverterImpl(jsonSchemaConverter);
  }

  @Bean
  @ConditionalOnMissingBean
  public ChatMessageConverter aiAgentLangchain4JChatMessageConverter(
      ContentConverter contentConverter,
      ToolCallConverter toolCallConverter,
      @ConnectorsObjectMapper ObjectMapper objectMapper) {
    return new ChatMessageConverterImpl(contentConverter, toolCallConverter, objectMapper);
  }
}
