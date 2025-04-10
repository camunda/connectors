/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.runtime.outbound.jobhandling;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.runtime.core.config.OutboundConnectorConfiguration;
import io.camunda.connector.runtime.core.outbound.ConnectorJobHandler;
import io.camunda.connector.runtime.core.outbound.ConnectorResult;
import io.camunda.connector.runtime.core.secret.SecretProviderAggregator;
import io.camunda.connector.runtime.metrics.ConnectorMetrics;
import io.camunda.connector.runtime.metrics.ConnectorMetrics.Outbound;
import io.camunda.document.factory.DocumentFactory;
import io.camunda.spring.client.jobhandling.CommandExceptionHandlingStrategy;
import io.camunda.spring.client.jobhandling.CommandWrapper;
import io.camunda.spring.client.metrics.MetricsRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An enhanced implementation of a {@link ConnectorJobHandler} that adds metrics, asynchronous
 * command execution, and retries.
 */
public class SpringConnectorJobHandler extends ConnectorJobHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(SpringConnectorJobHandler.class);

  private static final int MAX_ZEEBE_COMMAND_RETRIES = 3;

  private final CommandExceptionHandlingStrategy commandExceptionHandlingStrategy;
  private final MetricsRecorder metricsRecorder;
  private final OutboundConnectorConfiguration connectorConfiguration;

  public SpringConnectorJobHandler(
      MetricsRecorder metricsRecorder,
      CommandExceptionHandlingStrategy commandExceptionHandlingStrategy,
      SecretProviderAggregator secretProviderAggregator,
      ValidationProvider validationProvider,
      DocumentFactory documentFactory,
      ObjectMapper objectMapper,
      OutboundConnectorFunction connectorFunction,
      OutboundConnectorConfiguration connectorConfiguration) {
    super(
        connectorFunction,
        secretProviderAggregator,
        validationProvider,
        documentFactory,
        objectMapper);
    this.metricsRecorder = metricsRecorder;
    this.commandExceptionHandlingStrategy = commandExceptionHandlingStrategy;
    this.connectorConfiguration = connectorConfiguration;
  }

  @Override
  public void handle(JobClient client, ActivatedJob job) {
    String jobTypeAndId =
        job.getCustomHeaders().getOrDefault("id", "unknown")
            + "#"
            + job.getCustomHeaders().getOrDefault("version", "0");
    System.out.println("==================================>" + jobTypeAndId);
    metricsRecorder.increase(
        Outbound.METRIC_CONNECTOR_VERSION, Outbound.JOB_RECEIVED, jobTypeAndId);
    metricsRecorder.executeWithTimer(
        ConnectorMetrics.Outbound.METRIC_NAME_TIME,
        job.getType(),
        () -> {
          metricsRecorder.increase(
              Outbound.METRIC_NAME_INVOCATIONS,
              Outbound.ACTION_ACTIVATED,
              connectorConfiguration.type());
          try {
            super.handle(client, job);
          } catch (Exception e) {
            metricsRecorder.increase(
                Outbound.METRIC_NAME_INVOCATIONS,
                Outbound.ACTION_FAILED,
                connectorConfiguration.type());
            LOGGER.warn("Failed to handle job: {}", job);
          }
        });
  }

  @Override
  @SuppressWarnings({"unchecked", "rawtypes"})
  protected void failJob(JobClient client, ActivatedJob job, ConnectorResult.ErrorResult result) {
    try {
      metricsRecorder.increase(
          Outbound.METRIC_NAME_INVOCATIONS, Outbound.ACTION_FAILED, connectorConfiguration.type());
    } finally {
      FinalCommandStep commandStep = prepareFailJobCommand(client, job, result);
      new CommandWrapper(
              commandStep,
              job,
              commandExceptionHandlingStrategy,
              metricsRecorder,
              MAX_ZEEBE_COMMAND_RETRIES)
          .executeAsync();
    }
  }

  @Override
  @SuppressWarnings({"unchecked", "rawtypes"})
  protected void completeJob(
      JobClient client, ActivatedJob job, ConnectorResult.SuccessResult result) {
    try {
      metricsRecorder.increase(
          Outbound.METRIC_NAME_INVOCATIONS,
          Outbound.ACTION_COMPLETED,
          connectorConfiguration.type());
    } finally {
      FinalCommandStep commandStep = prepareCompleteJobCommand(client, job, result);
      new CommandWrapper(
              commandStep,
              job,
              commandExceptionHandlingStrategy,
              metricsRecorder,
              MAX_ZEEBE_COMMAND_RETRIES)
          .executeAsync();
    }
  }
}
