/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.capabilities;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.runtime.annotation.ConnectorsObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for the model capability matrix. Bundled defaults from {@code
 * resources/capabilities/model-capabilities.yaml} are loaded as a low-precedence property source by
 * {@link CapabilityMatrixEnvironmentPostProcessor}; library-consumer overrides under the same
 * {@code camunda.connector.agenticai.aiagent.framework.capabilities.*} prefix land on top.
 */
@Configuration
@EnableConfigurationProperties(AgenticAiFrameworkProperties.class)
public class AgenticAiCapabilitiesConfiguration {

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
