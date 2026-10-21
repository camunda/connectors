/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.otel.configuration;

import io.camunda.connector.agenticai.otel.AgentTracingListener;
import io.opentelemetry.api.OpenTelemetry;
import java.time.Clock;
import java.util.Optional;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Registers the OpenTelemetry tracing listener of the AI Agent. The module is inert unless tracing
 * is enabled and the runtime provides an {@link OpenTelemetry} bean, so it needs no OpenTelemetry
 * SDK of its own. It is ordered after Spring Boot's OpenTelemetry SDK auto-configuration so that
 * the bean condition sees the {@code OpenTelemetry} bean it provides. The listener reads time from
 * the {@link Clock} bean of the runtime and from the system clock when there is none.
 */
@AutoConfiguration(
    afterName =
        "org.springframework.boot.opentelemetry.autoconfigure.OpenTelemetrySdkAutoConfiguration")
@ConditionalOnBooleanProperty(value = "camunda.connector.agenticai.enabled", matchIfMissing = true)
@EnableConfigurationProperties(AgentTracingProperties.class)
public class AgentTracingAutoConfiguration {

  @Bean
  @ConditionalOnBooleanProperty("camunda.connector.agenticai.aiagent.tracing.enabled")
  @ConditionalOnBean(OpenTelemetry.class)
  public AgentTracingListener agentTracingListener(
      OpenTelemetry openTelemetry, AgentTracingProperties properties, Optional<Clock> clock) {
    return new AgentTracingListener(openTelemetry, properties, clock.orElseGet(Clock::systemUTC));
  }
}
