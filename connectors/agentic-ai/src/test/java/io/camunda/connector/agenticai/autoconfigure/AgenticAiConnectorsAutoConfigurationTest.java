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
import io.camunda.connector.agenticai.adhoctoolsschema.resolver.AdHocToolsSchemaResolver;
import io.camunda.connector.agenticai.adhoctoolsschema.resolver.CachingAdHocToolsSchemaResolver;
import io.camunda.connector.agenticai.adhoctoolsschema.resolver.CamundaClientAdHocToolsSchemaResolver;
import io.camunda.connector.agenticai.aiagent.AiAgentFunction;
import io.camunda.connector.agenticai.aiagent.agent.AiAgentRequestHandler;
import io.camunda.connector.agenticai.aiagent.provider.ChatModelFactory;
import io.camunda.connector.agenticai.aiagent.tools.ToolCallingHandler;
import io.camunda.connector.agenticai.aiagent.tools.ToolSpecificationConverter;
import io.camunda.connector.feel.FeelEngineWrapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

class AgenticAiConnectorsAutoConfigurationTest {

  private static final List<Class<?>> AGENTIC_AI_BEANS =
      List.of(
          AdHocToolsSchemaResolver.class,
          AdHocToolsSchemaFunction.class,
          ChatModelFactory.class,
          ToolSpecificationConverter.class,
          ToolCallingHandler.class,
          AiAgentRequestHandler.class,
          AiAgentFunction.class);

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(TestConfig.class)
          .withUserConfiguration(AgenticAiConnectorsAutoConfiguration.class);

  protected static class TestConfig {
    @Bean
    public ObjectMapper objectMapper() {
      return mock(ObjectMapper.class);
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
                    AGENTIC_AI_BEANS.stream().map(beanClass -> hasSingleBean(context, beanClass))));
  }

  @Test
  void whenAgenticAiConnectorsDisabled_thenNoAgenticConnectorBeansAreCreated() {
    contextRunner
        .withPropertyValues("camunda.connector.agenticai.enabled=false")
        .run(
            context ->
                assertAll(
                    AGENTIC_AI_BEANS.stream()
                        .map(beanClass -> doesNotHaveBean(context, beanClass))));
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

  private Executable hasSingleBean(AssertableApplicationContext context, Class<?> beanClass) {
    return () -> assertThat(context).hasSingleBean(beanClass);
  }

  private Executable doesNotHaveBean(AssertableApplicationContext context, Class<?> beanClass) {
    return () -> assertThat(context).doesNotHaveBean(beanClass);
  }
}
