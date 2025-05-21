/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.connector.agenticai.adhoctoolsschema.AdHocToolsSchemaFunction;
import io.camunda.connector.agenticai.adhoctoolsschema.feel.FeelInputParamExtractorImpl;
import io.camunda.connector.agenticai.adhoctoolsschema.resolver.AdHocToolsSchemaResolver;
import io.camunda.connector.agenticai.adhoctoolsschema.resolver.CachingAdHocToolsSchemaResolver;
import io.camunda.connector.agenticai.adhoctoolsschema.resolver.CamundaClientAdHocToolsSchemaResolver;
import io.camunda.connector.agenticai.adhoctoolsschema.resolver.schema.AdHocToolSchemaGenerator;
import io.camunda.connector.agenticai.aiagent.AiAgentFunction;
import io.camunda.connector.agenticai.aiagent.agent.AiAgentRequestHandler;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.ChatModelFactoryImpl;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.document.DocumentToContentConverterImpl;
import io.camunda.connector.feel.FeelEngineWrapper;
import java.util.List;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.validation.FieldError;

class AgenticAiConnectorsAutoConfigurationTest {

  private static final List<Class<?>> AGENTIC_AI_BEANS =
      List.of(
          FeelInputParamExtractorImpl.class,
          AdHocToolSchemaGenerator.class,
          AdHocToolsSchemaResolver.class,
          AdHocToolsSchemaFunction.class,
          ChatModelFactoryImpl.class,
          DocumentToContentConverterImpl.class,
          AiAgentRequestHandler.class,
          AiAgentFunction.class);

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(TestConfig.class)
          .withUserConfiguration(AgenticAiConnectorsAutoConfiguration.class);

  protected static class TestConfig {
    @Bean
    public ObjectMapper objectMapper() {
      return new ObjectMapper();
    }

    @Bean
    public CamundaClient camundaClient() {
      return mock(CamundaClient.class);
    }

    @Bean
    public FeelEngineWrapper feelEngineWrapper() {
      return mock(FeelEngineWrapper.class);
    }
  }

  @Test
  void whenAgenticAiConnectorsEnabled_thenAgenticConnectorBeansAreCreated() {
    contextRunner
        .withPropertyValues("camunda.connector.agenticai.enabled=true")
        .run(
            context ->
                assertAll(
                    AGENTIC_AI_BEANS.stream()
                        .map(beanClass -> () -> assertThat(context).hasSingleBean(beanClass))));
  }

  @Test
  void whenAgenticAiConnectorsDisabled_thenNoAgenticConnectorBeansAreCreated() {
    contextRunner
        .withPropertyValues("camunda.connector.agenticai.enabled=false")
        .run(
            context ->
                assertAll(
                    AGENTIC_AI_BEANS.stream()
                        .map(beanClass -> () -> assertThat(context).doesNotHaveBean(beanClass))));
  }

  @Test
  void whenToolsCachingDisabled_thenConfiguresDefaultToolsSchemaResolver() {
    contextRunner
        .withPropertyValues("camunda.connector.agenticai.tools.cache.enabled=false")
        .run(
            context ->
                assertThat(context)
                    .getBean(AdHocToolsSchemaResolver.class)
                    .isInstanceOf(CamundaClientAdHocToolsSchemaResolver.class));
  }

  @Test
  void whenToolsCachingEnabled_thenConfiguresCachingToolsSchemaResolver() {
    contextRunner
        .withPropertyValues("camunda.connector.agenticai.tools.cache.enabled=true")
        .run(
            context ->
                assertThat(context)
                    .getBean(AdHocToolsSchemaResolver.class)
                    .isInstanceOf(CachingAdHocToolsSchemaResolver.class));
  }

  @Test
  void whenToolsCachingMaximumSizeIsNegative_thenFailsValidation() {
    contextRunner
        .withPropertyValues("camunda.connector.agenticai.tools.cache.maximumSize=-10")
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .hasRootCauseInstanceOf(BindValidationException.class)
                    .rootCause()
                    .isInstanceOfSatisfying(
                        BindValidationException.class,
                        e -> {
                          assertThat(e.getValidationErrors().getAllErrors())
                              .hasSize(1)
                              .first(InstanceOfAssertFactories.type(FieldError.class))
                              .extracting(
                                  FieldError::getObjectName,
                                  FieldError::getField,
                                  FieldError::getRejectedValue,
                                  FieldError::getDefaultMessage)
                              .containsExactly(
                                  "camunda.connector.agenticai",
                                  "tools.cache.maximumSize",
                                  -10L,
                                  "must be greater than or equal to 0");
                        }));
  }
}
