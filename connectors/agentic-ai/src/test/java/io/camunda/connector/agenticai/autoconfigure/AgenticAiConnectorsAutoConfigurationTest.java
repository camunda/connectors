/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.connector.agenticai.adhoctoolsschema.AdHocToolsSchemaFunction;
import io.camunda.connector.agenticai.adhoctoolsschema.resolver.AdHocToolsSchemaResolver;
import io.camunda.connector.agenticai.adhoctoolsschema.resolver.CachingAdHocToolsSchemaResolver;
import io.camunda.connector.agenticai.adhoctoolsschema.resolver.CamundaClientAdHocToolsSchemaResolver;
import io.camunda.connector.agenticai.aiagent.AiAgentFunction;
import io.camunda.connector.agenticai.aiagent.agent.AiAgentRequestHandler;
import io.camunda.connector.feel.FeelEngineWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

class AgenticAiConnectorsAutoConfigurationTest {

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
                    () -> assertThat(context).hasSingleBean(AdHocToolsSchemaResolver.class),
                    () -> assertThat(context).hasSingleBean(AdHocToolsSchemaFunction.class),
                    () -> assertThat(context).hasSingleBean(AiAgentRequestHandler.class),
                    () -> assertThat(context).hasSingleBean(AiAgentFunction.class)));
  }

  @Test
  void whenAgenticAiConnectorsDisabled_thenNoAgenticConnectorBeansAreCreated() {
    contextRunner
        .withPropertyValues("camunda.connector.agenticai.enabled=false")
        .run(
            context ->
                assertAll(
                    () -> assertThat(context).doesNotHaveBean(AdHocToolsSchemaResolver.class),
                    () -> assertThat(context).doesNotHaveBean(AdHocToolsSchemaFunction.class),
                    () -> assertThat(context).doesNotHaveBean(AiAgentRequestHandler.class),
                    () -> assertThat(context).doesNotHaveBean(AiAgentFunction.class)));
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
}
