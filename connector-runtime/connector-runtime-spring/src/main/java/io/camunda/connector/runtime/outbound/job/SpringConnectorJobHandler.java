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
package io.camunda.connector.runtime.outbound.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.command.ThrowErrorCommandStep1;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.CompleteJobResponse;
import io.camunda.client.api.response.FailJobResponse;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.api.worker.JobHandler;
import io.camunda.client.jobhandling.CommandExceptionHandlingStrategy;
import io.camunda.client.jobhandling.CommandWrapper;
import io.camunda.client.metrics.MetricsContext.CounterMetricsContext;
import io.camunda.client.metrics.MetricsContext.TimerMetricsContext;
import io.camunda.client.metrics.MetricsRecorder;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.runtime.core.ConnectorResultHandler;
import io.camunda.connector.runtime.core.Keywords;
import io.camunda.connector.runtime.core.error.BpmnError;
import io.camunda.connector.runtime.core.error.ConnectorError;
import io.camunda.connector.runtime.core.error.InvalidBackOffDurationException;
import io.camunda.connector.runtime.core.error.JobError;
import io.camunda.connector.runtime.core.outbound.*;
import io.camunda.connector.runtime.core.secret.SecretProviderAggregator;
import io.camunda.connector.runtime.core.secret.SecretProviderDiscovery;
import io.camunda.connector.runtime.metrics.ConnectorsOutboundMetricsContext;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An enhanced implementation of a {@link JobHandler} that adds metrics, asynchronous command
 * execution, and retries.
 */
public class SpringConnectorJobHandler implements JobHandler {

  // Protects Zeebe from enormously large messages it cannot handle
  static final int MAX_ERROR_MESSAGE_LENGTH = 6000;
  private static final Logger LOGGER = LoggerFactory.getLogger(SpringConnectorJobHandler.class);
  private static final int MAX_ZEEBE_COMMAND_RETRIES = 3;
  protected final OutboundConnectorFunction call;
  private final CommandExceptionHandlingStrategy commandExceptionHandlingStrategy;
  private final MetricsRecorder connectorsOutboundMetrics;
  private final OutboundConnectorExceptionHandler outboundConnectorExceptionHandler;
  private final ConnectorResultHandler connectorResultHandler;
  protected SecretProvider secretProvider;
  protected ValidationProvider validationProvider;
  protected DocumentFactory documentFactory;
  protected ObjectMapper objectMapper;

  public SpringConnectorJobHandler(
      MetricsRecorder outboundMetrics,
      CommandExceptionHandlingStrategy commandExceptionHandlingStrategy,
      SecretProviderAggregator secretProviderAggregator,
      ValidationProvider validationProvider,
      DocumentFactory documentFactory,
      ObjectMapper objectMapper,
      OutboundConnectorFunction connectorFunction) {
    this.call = connectorFunction;
    this.secretProvider = secretProviderAggregator;
    this.validationProvider = validationProvider;
    this.documentFactory = documentFactory;
    this.objectMapper = objectMapper;
    this.outboundConnectorExceptionHandler =
        new OutboundConnectorExceptionHandler(getSecretProvider());
    this.connectorResultHandler = new ConnectorResultHandler(objectMapper);
    this.commandExceptionHandlingStrategy = commandExceptionHandlingStrategy;
    this.connectorsOutboundMetrics = outboundMetrics;
  }

  protected static FinalCommandStep<CompleteJobResponse> prepareCompleteJobCommand(
      JobClient client, ActivatedJob job, ConnectorResult.SuccessResult result) {
    return client.newCompleteCommand(job).variables(result.variables());
  }

  protected static FinalCommandStep<FailJobResponse> prepareFailJobCommand(
      JobClient client, ActivatedJob job, ConnectorResult.ErrorResult result) {
    var retries = result.retries();
    var errorMessage = truncateErrorMessage(result.exception().getMessage());
    Duration backoff = result.retryBackoff();
    var command =
        client.newFailCommand(job).retries(Math.max(retries, 0)).errorMessage(errorMessage);
    if (backoff != null) {
      command = command.retryBackoff(backoff);
    }
    if (result.responseValue() != null) {
      command = command.variables(result.responseValue());
    }
    return command;
  }

  protected static ThrowErrorCommandStep1.ThrowErrorCommandStep2 prepareThrowBpmnErrorCommand(
      JobClient client, ActivatedJob job, BpmnError error) {
    return client
        .newThrowErrorCommand(job)
        .errorCode(error.errorCode())
        .variables(error.variables())
        .errorMessage(truncateErrorMessage(error.errorMessage()));
  }

  private static String truncateErrorMessage(String message) {
    return message != null
        ? message.substring(0, Math.min(message.length(), MAX_ERROR_MESSAGE_LENGTH))
        : null;
  }

  private SecretProvider getSecretProvider() {
    // if custom provider / aggregator is provided by the runtime, use it
    if (secretProvider != null) {
      return secretProvider;
    }
    // otherwise fall back to default implementation (SPI discovery)
    return new SecretProviderAggregator(SecretProviderDiscovery.discoverSecretProviders());
  }

  @Override
  public void handle(JobClient client, ActivatedJob job) {
    CounterMetricsContext counterMetricsContext = ConnectorsOutboundMetricsContext.counter(job);
    TimerMetricsContext timerMetricsContext = ConnectorsOutboundMetricsContext.timer(job);
    connectorsOutboundMetrics.increaseActivated(counterMetricsContext);
    connectorsOutboundMetrics.executeWithTimer(
        timerMetricsContext, () -> this.executeJob(client, job, counterMetricsContext));
  }

  private void executeJob(
      JobClient client, ActivatedJob job, CounterMetricsContext counterMetricsContext) {
    try {
      internalHandle(client, job, counterMetricsContext);
    } catch (Exception e) {
      connectorsOutboundMetrics.increaseFailed(counterMetricsContext);
      LOGGER.warn("Failed to handle job: {} of type: {}", job.getKey(), job.getType());
    }
  }

  public void internalHandle(
      final JobClient client,
      final ActivatedJob job,
      final CounterMetricsContext counterMetricsContext) {
    LOGGER.info(
        "Received job: {} of type: {} for tenant: {}",
        job.getKey(),
        job.getType(),
        job.getTenantId());
    ConnectorResult result = getConnectorResult(job);
    processFinalResult(client, job, result, counterMetricsContext);
  }

  private ConnectorResult getConnectorResult(ActivatedJob job) {
    Duration retryBackoff = null;
    try {
      retryBackoff = getBackoffDuration(job);
      var context =
          new JobHandlerContext(
              job, getSecretProvider(), validationProvider, documentFactory, objectMapper);
      var response = call.execute(context);
      var responseVariables =
          connectorResultHandler.createOutputVariables(
              response,
              job.getCustomHeaders().get(Keywords.RESULT_VARIABLE_KEYWORD),
              job.getCustomHeaders().get(Keywords.RESULT_EXPRESSION_KEYWORD));
      return new ConnectorResult.SuccessResult(response, responseVariables);
    } catch (Exception e) {
      return outboundConnectorExceptionHandler.manageConnectorJobHandlerException(
          e, job, retryBackoff);
    }
  }

  private void processFinalResult(
      JobClient client,
      ActivatedJob job,
      ConnectorResult finalResult,
      CounterMetricsContext counterMetricsContext) {
    try {
      Optional<ConnectorError> optionalConnectorError =
          connectorResultHandler.examineErrorExpression(
              finalResult.responseValue(),
              job.getCustomHeaders(),
              new ErrorExpressionJobContext(
                  new ErrorExpressionJobContext.ErrorExpressionJob(job.getRetries())));
      optionalConnectorError.ifPresentOrElse(
          error -> handleBPMNError(client, job, error, counterMetricsContext),
          () -> handleSuccessResult(client, job, finalResult, counterMetricsContext));
    } catch (Exception ex) {
      failJob(
          client,
          job,
          this.outboundConnectorExceptionHandler.handleFinalResultException(ex, job),
          counterMetricsContext);
    }
  }

  private void handleSuccessResult(
      JobClient jobClient,
      ActivatedJob job,
      ConnectorResult finalResult,
      CounterMetricsContext counterMetricsContext) {
    if (finalResult instanceof ConnectorResult.SuccessResult successResult) {
      LOGGER.info("Completing job: {} for tenant: {}", job.getKey(), job.getTenantId());
      completeJob(jobClient, job, successResult, counterMetricsContext);
    } else if (finalResult instanceof ConnectorResult.ErrorResult errorResult) {
      // Handle Java error, e.g. ConnectorException
      // these errors won't be handled ConnectorHelper.examineErrorExpression
      LOGGER.error(
          "Exception while completing job: {}, message: {}",
          JobForLog.from(job),
          errorResult.exception().getMessage(),
          errorResult.exception());
      failJob(jobClient, job, errorResult, counterMetricsContext);
    }
  }

  private void handleBPMNError(
      JobClient client,
      ActivatedJob job,
      ConnectorError error,
      CounterMetricsContext counterMetricsContext) {
    if (error instanceof BpmnError bpmnError) {
      LOGGER.debug(
          "Throwing BPMN error for job {} with code {}", job.getKey(), bpmnError.errorCode());
      throwBpmnError(client, job, bpmnError, counterMetricsContext);
    } else if (error instanceof JobError jobError) {
      LOGGER.debug("Throwing incident for job {}", job.getKey());
      failJob(
          client,
          job,
          new ConnectorResult.ErrorResult(
              Map.of("error", jobError.errorMessage()),
              new RuntimeException(jobError.errorMessage()),
              jobError.retries(),
              jobError.retryBackoff()),
          counterMetricsContext);
    }
  }

  private Duration getBackoffDuration(ActivatedJob job) {
    String backoffHeader = job.getCustomHeaders().get(Keywords.RETRY_BACKOFF_KEYWORD);
    if (backoffHeader == null) {
      return null;
    }
    try {
      return Duration.parse(backoffHeader);
    } catch (DateTimeParseException e) {
      throw new InvalidBackOffDurationException(
          "Failed to parse retry backoff header. Expected ISO-8601 duration, e.g. PT5M, "
              + "got: "
              + job.getCustomHeaders().get(Keywords.RETRY_BACKOFF_KEYWORD),
          e);
    }
  }

  @SuppressWarnings("rawtypes")
  protected void failJob(
      JobClient client,
      ActivatedJob job,
      ConnectorResult.ErrorResult result,
      CounterMetricsContext counterMetricsContext) {

    FinalCommandStep commandStep = prepareFailJobCommand(client, job, result);
    new CommandWrapper(
            commandStep,
            job,
            commandExceptionHandlingStrategy,
            connectorsOutboundMetrics,
            counterMetricsContext,
            MAX_ZEEBE_COMMAND_RETRIES)
        .executeAsync();
  }

  @SuppressWarnings("rawtypes")
  protected void throwBpmnError(
      JobClient client,
      ActivatedJob job,
      BpmnError value,
      CounterMetricsContext counterMetricsContext) {
    FinalCommandStep commandStep = prepareThrowBpmnErrorCommand(client, job, value);
    new CommandWrapper(
            commandStep,
            job,
            commandExceptionHandlingStrategy,
            connectorsOutboundMetrics,
            counterMetricsContext,
            MAX_ZEEBE_COMMAND_RETRIES)
        .executeAsync();
  }

  @SuppressWarnings("rawtypes")
  protected void completeJob(
      JobClient client,
      ActivatedJob job,
      ConnectorResult.SuccessResult result,
      CounterMetricsContext counterMetricsContext) {

    FinalCommandStep commandStep = prepareCompleteJobCommand(client, job, result);
    new CommandWrapper(
            commandStep,
            job,
            commandExceptionHandlingStrategy,
            connectorsOutboundMetrics,
            counterMetricsContext,
            MAX_ZEEBE_COMMAND_RETRIES)
        .executeAsync();
  }
}
