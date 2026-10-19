/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.adhoctoolsschema.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.connector.agenticai.adhoctoolsschema.AdHocToolsSchemaFunction;
import io.camunda.connector.agenticai.tools.schema.AdHocToolsSchemaResolver;
import io.camunda.connector.agenticai.tools.schema.processdefinition.ProcessDefinitionAdHocToolElementsResolver;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

class AdHocToolsSchemaConnectorAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(TestConfig.class)
          .withConfiguration(
              AutoConfigurations.of(AdHocToolsSchemaConnectorAutoConfiguration.class));

  @Test
  void whenAdHocToolsSchemaConnectorDisabled_thenNoAdHocToolsSchemaFunctionIsCreated() {
    contextRunner.run(context -> assertThat(context).hasSingleBean(AdHocToolsSchemaFunction.class));

    contextRunner
        .withPropertyValues(
            "camunda.connector.agenticai.ad-hoc-tools-schema-resolver.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(AdHocToolsSchemaFunction.class));
  }

  static class TestConfig {

    @Bean
    ProcessDefinitionAdHocToolElementsResolver toolElementsResolver() {
      return mock(ProcessDefinitionAdHocToolElementsResolver.class);
    }

    @Bean
    AdHocToolsSchemaResolver toolsSchemaResolver() {
      return mock(AdHocToolsSchemaResolver.class);
    }
  }
}
