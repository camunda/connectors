/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.ChatMessageConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.ChatMessageConverterImpl;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.ChatModelHttpProxySupport;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.ContentConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.ContentConverterImpl;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.document.DocumentToContentConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.document.DocumentToContentConverterImpl;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.jsonschema.JsonSchemaConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.tool.ToolCallConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.tool.ToolCallConverterImpl;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.tool.ToolSpecificationConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.tool.ToolSpecificationConverterImpl;
import io.camunda.connector.agenticai.common.configuration.AgenticAiCommonAutoConfiguration;
import io.camunda.connector.agenticai.common.http.AgenticAiHttpProxySupport;
import io.camunda.connector.runtime.annotation.ConnectorsObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * The LangChain4j bridge: the converter chain and the proxy support that a custom {@code
 * ChatModelFactory} uses to build a {@link
 * io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.LangChain4JChatModel}.
 *
 * <p>It is off by default and loads only with {@code
 * camunda.connector.agenticai.aiagent.langchain4j.enabled=true}, so a runtime without a custom
 * LangChain4j provider carries none of it. It is a self-contained auto-configuration that depends
 * only on the common module, so it can move into its own module without a change of its contract.
 */
@AutoConfiguration(after = AgenticAiCommonAutoConfiguration.class)
@ConditionalOnBooleanProperty(value = "camunda.connector.agenticai.enabled", matchIfMissing = true)
@ConditionalOnProperty(
    name = "camunda.connector.agenticai.aiagent.langchain4j.enabled",
    havingValue = "true")
public class AgenticAiLangChain4JAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public DocumentToContentConverter langChain4JDocumentToContentConverter() {
    return new DocumentToContentConverterImpl();
  }

  @Bean
  @ConditionalOnMissingBean
  public ContentConverter langChain4JContentConverter(
      @ConnectorsObjectMapper ObjectMapper objectMapper,
      DocumentToContentConverter documentToContentConverter) {
    return new ContentConverterImpl(objectMapper, documentToContentConverter);
  }

  @Bean
  @ConditionalOnMissingBean
  public ToolCallConverter langChain4JToolCallConverter(
      @ConnectorsObjectMapper ObjectMapper objectMapper) {
    return new ToolCallConverterImpl(objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public JsonSchemaConverter langChain4JJsonSchemaConverter(
      @ConnectorsObjectMapper ObjectMapper objectMapper) {
    return new JsonSchemaConverter(objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public ToolSpecificationConverter langChain4JToolSpecificationConverter(
      JsonSchemaConverter jsonSchemaConverter) {
    return new ToolSpecificationConverterImpl(jsonSchemaConverter);
  }

  @Bean
  @ConditionalOnMissingBean
  public ChatMessageConverter langChain4JChatMessageConverter(
      ContentConverter contentConverter,
      ToolCallConverter toolCallConverter,
      @ConnectorsObjectMapper ObjectMapper objectMapper) {
    return new ChatMessageConverterImpl(contentConverter, toolCallConverter, objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public ChatModelHttpProxySupport langChain4JChatModelHttpProxySupport(
      AgenticAiHttpProxySupport httpProxySupport) {
    return new ChatModelHttpProxySupport(httpProxySupport.getJdkHttpClientProxyConfigurator());
  }
}
