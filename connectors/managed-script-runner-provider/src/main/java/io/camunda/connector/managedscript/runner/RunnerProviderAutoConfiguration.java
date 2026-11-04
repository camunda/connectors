/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.managedscript.runner;

import io.camunda.connector.managedscript.api.ManagedCodeProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Provides the runner provider only when managed code is enabled and selects it, so that a runtime
 * that uses another provider needs no runner configuration.
 */
@AutoConfiguration
@ConditionalOnBooleanProperty("camunda.connector.managed-code.enabled")
@ConditionalOnProperty(
    prefix = "camunda.connector.managed-code",
    name = "provider",
    havingValue = RunnerProvider.NAME)
@EnableConfigurationProperties(RunnerProviderProperties.class)
public class RunnerProviderAutoConfiguration {

  @Bean
  ManagedCodeProvider runnerProvider(RunnerProviderProperties properties) {
    return new RunnerProvider(properties);
  }
}
