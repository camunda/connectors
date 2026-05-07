/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.capabilities;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.runtime.annotation.ConnectorsObjectMapper;
import java.io.IOException;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * Spring configuration for the model capability matrix.
 *
 * <p>Bundled defaults from {@code resources/capabilities/model-capabilities.yaml} are loaded as a
 * low-precedence {@link PropertySource} via {@link #setEnvironment(Environment)} — invoked when
 * Spring instantiates this configuration bean and therefore robust against runtimes that skip
 * Spring Boot's {@code EnvironmentPostProcessor} discovery (e.g. when the connector jar is loaded
 * after the host application context has already started). Library-consumer overrides under the
 * same {@code camunda.connector.agenticai.aiagent.framework.capabilities.*} prefix land on top.
 */
@Configuration
@EnableConfigurationProperties(AgenticAiFrameworkProperties.class)
public class AgenticAiCapabilitiesConfiguration implements EnvironmentAware {

  private static final String BUNDLED_RESOURCE = "capabilities/model-capabilities.yaml";
  private static final String PROPERTY_SOURCE_NAME = "agentic-ai-bundled-capability-matrix";

  @Override
  public void setEnvironment(Environment environment) {
    if (!(environment instanceof ConfigurableEnvironment configurable)) {
      return;
    }
    if (configurable.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
      return;
    }
    final var resource = new ClassPathResource(BUNDLED_RESOURCE);
    if (!resource.exists()) {
      return;
    }
    try {
      final List<PropertySource<?>> sources =
          new YamlPropertySourceLoader().load(PROPERTY_SOURCE_NAME, resource);
      sources.forEach(configurable.getPropertySources()::addLast);
    } catch (IOException e) {
      throw new IllegalStateException(
          "Failed to load bundled capability matrix from classpath:" + BUNDLED_RESOURCE, e);
    }
  }

  @Bean
  @ConditionalOnMissingBean
  public CapabilityMatrix aiAgentCapabilityMatrix(
      AgenticAiFrameworkProperties properties, @ConnectorsObjectMapper ObjectMapper objectMapper) {
    return CapabilityMatrixFactory.build(properties, objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public ModelCapabilitiesResolver aiAgentModelCapabilitiesResolver(
      CapabilityMatrix matrix, @ConnectorsObjectMapper ObjectMapper objectMapper) {
    return new ModelCapabilitiesResolver(matrix, objectMapper);
  }
}
