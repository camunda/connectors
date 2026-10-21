/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.ChatMessageConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.ChatModelHttpProxySupport;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.ContentConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.ToolCallMetadataDecorator;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.document.DocumentToContentConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.jsonschema.JsonSchemaConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.tool.ToolCallConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.tool.ToolSpecificationConverter;
import io.camunda.connector.agenticai.common.configuration.AgenticAiCommonAutoConfiguration;
import io.camunda.connector.runtime.annotation.ConnectorsObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

class AgenticAiLangChain4JAutoConfigurationTest {

  private static final List<Class<?>> LANGCHAIN4J_BEANS =
      List.of(
          ChatModelHttpProxySupport.class,
          ToolCallMetadataDecorator.class,
          DocumentToContentConverter.class,
          ContentConverter.class,
          ToolCallConverter.class,
          JsonSchemaConverter.class,
          ToolSpecificationConverter.class,
          ChatMessageConverter.class);

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(TestConfig.class)
          .withConfiguration(
              AutoConfigurations.of(
                  AgenticAiCommonAutoConfiguration.class,
                  AgenticAiLangChain4JAutoConfiguration.class));

  @Test
  void whenAgenticAiConfigurationEnabled_thenTheBridgeBeansAreCreated() {
    contextRunner
        .withPropertyValues("camunda.connector.agenticai.enabled=true")
        .run(context -> LANGCHAIN4J_BEANS.forEach(type -> assertThat(context).hasSingleBean(type)));
  }

  @Test
  void whenAgenticAiConfigurationIsNotSet_thenTheBridgeBeansAreCreated() {
    contextRunner.run(
        context -> LANGCHAIN4J_BEANS.forEach(type -> assertThat(context).hasSingleBean(type)));
  }

  @Test
  void whenAgenticAiConfigurationDisabled_thenNoBridgeBeansAreCreated() {
    contextRunner
        .withPropertyValues("camunda.connector.agenticai.enabled=false")
        .run(
            (AssertableApplicationContext context) ->
                LANGCHAIN4J_BEANS.forEach(type -> assertThat(context).doesNotHaveBean(type)));
  }

  @Test
  void aUserProvidedToolCallMetadataDecoratorReplacesTheDefault() {
    final var decorator = new ToolCallMetadataDecorator() {};
    contextRunner
        .withBean("customDecorator", ToolCallMetadataDecorator.class, () -> decorator)
        .run(
            context ->
                assertThat(context.getBean(ToolCallMetadataDecorator.class)).isSameAs(decorator));
  }

  static class TestConfig {

    @Bean
    @ConnectorsObjectMapper
    public ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }
}
