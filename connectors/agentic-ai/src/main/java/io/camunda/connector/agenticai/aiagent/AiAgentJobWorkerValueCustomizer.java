/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent;

import io.camunda.connector.runtime.core.config.ConnectorConfigurationOverrides;
import io.camunda.spring.client.annotation.customizer.JobWorkerValueCustomizer;
import io.camunda.spring.client.annotation.value.JobWorkerValue;
import java.time.Duration;
import org.springframework.core.env.Environment;

/**
 * Allows to set override properties for the job worker implementation in the same way as for the
 * outbound connector by setting the following environment variables:
 *
 * <ul>
 *   <li>CONNECTOR_AI_AGENT_JOB_WORKER_TYPE
 *   <li>CONNECTOR_AI_AGENT_JOB_WORKER_TIMEOUT
 * </ul>
 */
public class AiAgentJobWorkerValueCustomizer implements JobWorkerValueCustomizer {

  private final Environment environment;

  public AiAgentJobWorkerValueCustomizer(Environment environment) {
    this.environment = environment;
  }

  @Override
  public void customize(JobWorkerValue jobWorkerValue) {
    if (!AiAgentJobWorker.JOB_WORKER_NAME.equals(jobWorkerValue.getName())) {
      return;
    }

    final var overrides =
        new ConnectorConfigurationOverrides(jobWorkerValue.getName(), environment::getProperty);
    overrides.typeOverride().ifPresent(jobWorkerValue::setType);
    overrides.timeoutOverride().map(Duration::ofMillis).ifPresent(jobWorkerValue::setTimeout);
  }
}
