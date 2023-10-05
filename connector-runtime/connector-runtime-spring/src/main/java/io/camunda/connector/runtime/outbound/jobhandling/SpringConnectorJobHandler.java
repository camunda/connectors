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
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.runtime.core.config.OutboundConnectorConfiguration;
import io.camunda.connector.runtime.core.error.BpmnError;
import io.camunda.connector.runtime.core.outbound.ConnectorJobHandler;
import io.camunda.connector.runtime.core.outbound.ConnectorResult;
import io.camunda.connector.runtime.core.secret.SecretProviderAggregator;
import io.camunda.connector.runtime.metrics.ConnectorMetrics;
import io.camunda.connector.runtime.metrics.ConnectorMetrics.Outbound;
import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.spring.client.jobhandling.CommandExceptionHandlingStrategy;
import io.camunda.zeebe.spring.client.jobhandling.CommandWrapper;
import io.camunda.zeebe.spring.client.metrics.MetricsRecorder;

/**
 * An enhanced implementation of a {@link ConnectorJobHandler} that adds metrics, asynchronous
 * command execution, and retries.
 */
public class SpringConnectorJobHandler extends ConnectorJobHandler {

  private final CommandExceptionHandlingStrategy commandExceptionHandlingStrategy;
  private final MetricsRecorder metricsRecorder;
  private final OutboundConnectorConfiguration connectorConfiguration;

  public SpringConnectorJobHandler(
      MetricsRecorder metricsRecorder,
      CommandExceptionHandlingStrategy commandExceptionHandlingStrategy,
      SecretProviderAggregator secretProviderAggregator,
      ValidationProvider validationProvider,
      ObjectMapper objectMapper,
      OutboundConnectorFunction connectorFunction,
      OutboundConnectorConfiguration connectorConfiguration) {
    super(connectorFunction, secretProviderAggregator, validationProvider, objectMapper);
    this.metricsRecorder = metricsRecorder;
    this.commandExceptionHandlingStrategy = commandExceptionHandlingStrategy;
    this.connectorConfiguration = connectorConfiguration;
  }

  @Override
  public void handle(JobClient client, ActivatedJob job) {
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
            System.out.println("Failed to handle job: " + job);
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
      new CommandWrapper(commandStep, job, commandExceptionHandlingStrategy).executeAsync();
    }
  }

  @Override
  protected void throwBpmnError(JobClient client, ActivatedJob job, BpmnError value) {
    try {
      metricsRecorder.increase(
          Outbound.METRIC_NAME_INVOCATIONS,
          Outbound.ACTION_BPMN_ERROR,
          connectorConfiguration.type());
    } finally {
      new CommandWrapper(
              prepareThrowBpmnErrorCommand(client, job, value),
              job,
              commandExceptionHandlingStrategy)
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
      new CommandWrapper(commandStep, job, commandExceptionHandlingStrategy).executeAsync();
    }
  }
}
