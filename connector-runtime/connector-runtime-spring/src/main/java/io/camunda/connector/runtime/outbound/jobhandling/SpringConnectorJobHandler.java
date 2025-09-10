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
import io.camunda.client.jobhandling.CommandExceptionHandlingStrategy;
import io.camunda.client.jobhandling.CommandWrapper;
import io.camunda.client.metrics.DefaultNoopMetricsRecorder;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.runtime.core.error.BpmnError;
import io.camunda.connector.runtime.core.outbound.ConnectorJobHandler;
import io.camunda.connector.runtime.core.outbound.ConnectorResult;
import io.camunda.connector.runtime.core.secret.SecretProviderAggregator;
import io.camunda.connector.runtime.metrics.ConnectorsOutboundMetrics;
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
  private final ConnectorsOutboundMetrics connectorsOutboundMetrics;
  private final DefaultNoopMetricsRecorder defaultNoopMetricsRecorder;

  public SpringConnectorJobHandler(
      ConnectorsOutboundMetrics outboundMetrics,
      CommandExceptionHandlingStrategy commandExceptionHandlingStrategy,
      SecretProviderAggregator secretProviderAggregator,
      ValidationProvider validationProvider,
      DocumentFactory documentFactory,
      ObjectMapper objectMapper,
      OutboundConnectorFunction connectorFunction,
      DefaultNoopMetricsRecorder defaultNoopMetricsRecorder) {
    super(
        connectorFunction,
        secretProviderAggregator,
        validationProvider,
        documentFactory,
        objectMapper);
    this.commandExceptionHandlingStrategy = commandExceptionHandlingStrategy;
    this.connectorsOutboundMetrics = outboundMetrics;
    this.defaultNoopMetricsRecorder = defaultNoopMetricsRecorder;
  }

  @Override
  public void handle(JobClient client, ActivatedJob job) {
    connectorsOutboundMetrics.increaseInvocation(job);
    connectorsOutboundMetrics.executeWithTimer(job, () -> this.executeJob(client, job));
  }

  private void executeJob(JobClient client, ActivatedJob job) {
    try {
      super.handle(client, job);
    } catch (Exception e) {
      connectorsOutboundMetrics.increaseFailure(job);
      LOGGER.warn("Failed to handle job: {} of type: {}", job.getKey(), job.getType());
      // don't let the job active if something goes wrong
      client
          .newFailCommand(job)
          .retries(0)
          .errorMessage("Unhandled exception occurred: " + e.getMessage())
          .send()
          .join(30, java.util.concurrent.TimeUnit.SECONDS);
    }
  }

  @Override
  @SuppressWarnings("rawtypes")
  protected void failJob(JobClient client, ActivatedJob job, ConnectorResult.ErrorResult result) {
    try {
      connectorsOutboundMetrics.increaseFailure(job);
    } finally {
      FinalCommandStep commandStep = prepareFailJobCommand(client, job, result);
      new CommandWrapper(
              commandStep,
              job,
              commandExceptionHandlingStrategy,
              defaultNoopMetricsRecorder,
              MAX_ZEEBE_COMMAND_RETRIES)
          .executeAsync();
    }
  }

  @Override
  @SuppressWarnings("rawtypes")
  protected void throwBpmnError(JobClient client, ActivatedJob job, BpmnError value) {
    try {
      connectorsOutboundMetrics.increaseBpmnError(job);
    } finally {
      FinalCommandStep commandStep = prepareThrowBpmnErrorCommand(client, job, value);
      new CommandWrapper(
              commandStep,
              job,
              commandExceptionHandlingStrategy,
              defaultNoopMetricsRecorder,
              MAX_ZEEBE_COMMAND_RETRIES)
          .executeAsync();
    }
  }

  @Override
  @SuppressWarnings("rawtypes")
  protected void completeJob(
      JobClient client, ActivatedJob job, ConnectorResult.SuccessResult result) {
    try {
      connectorsOutboundMetrics.increaseCompletion(job);
    } finally {
      FinalCommandStep commandStep = prepareCompleteJobCommand(client, job, result);
      new CommandWrapper(
              commandStep,
              job,
              commandExceptionHandlingStrategy,
              defaultNoopMetricsRecorder,
              MAX_ZEEBE_COMMAND_RETRIES)
          .executeAsync();
    }
  }
}
