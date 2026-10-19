/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.otel.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.otel.AgentTracingListener;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class AgentTracingAutoConfigurationTest {

  private static final String TRACING_ENABLED =
      "camunda.connector.agenticai.aiagent.tracing.enabled=true";

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(AgentTracingAutoConfiguration.class));

  @Test
  void registersNoListenerByDefault() {
    contextRunner
        .withUserConfiguration(OpenTelemetryConfig.class)
        .run(context -> assertThat(context).doesNotHaveBean(AgentTracingListener.class));
  }

  @Test
  void registersNoListenerWithoutOpenTelemetryBean() {
    contextRunner
        .withPropertyValues(TRACING_ENABLED)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(AgentTracingListener.class);
            });
  }

  @Test
  void registersListenerWhenEnabledWithOpenTelemetryBean() {
    contextRunner
        .withUserConfiguration(OpenTelemetryConfig.class)
        .withPropertyValues(TRACING_ENABLED)
        .run(context -> assertThat(context).hasSingleBean(AgentTracingListener.class));
  }

  @Test
  void bindsTheClusterIdAndCaptureFlag() {
    contextRunner
        .withUserConfiguration(OpenTelemetryConfig.class)
        .withPropertyValues(
            TRACING_ENABLED,
            "camunda.connector.agenticai.aiagent.tracing.capture-content=true",
            "camunda.connector.agenticai.aiagent.tracing.cluster-id=prod-eu")
        .run(
            context -> {
              final var properties = context.getBean(AgentTracingProperties.class);
              assertThat(properties.captureContent()).isTrue();
              assertThat(properties.clusterId()).isEqualTo("prod-eu");
            });
  }

  @Test
  void registersNothingWhenAgenticAiIsDisabled() {
    contextRunner
        .withUserConfiguration(OpenTelemetryConfig.class)
        .withPropertyValues(TRACING_ENABLED, "camunda.connector.agenticai.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(AgentTracingListener.class);
              assertThat(context).doesNotHaveBean(AgentTracingProperties.class);
            });
  }

  @Configuration(proxyBeanMethods = false)
  static class OpenTelemetryConfig {
    @Bean
    OpenTelemetry openTelemetry() {
      return OpenTelemetry.noop();
    }
  }
}
