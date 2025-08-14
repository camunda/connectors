/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent;

import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.FailJobResponse;
import io.camunda.client.api.worker.JobClient;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.runtime.core.Keywords;
import io.camunda.connector.runtime.core.error.InvalidBackOffDurationException;
import io.camunda.connector.runtime.core.outbound.ConnectorResult;
import io.camunda.connector.runtime.core.outbound.ConnectorResult.ErrorResult;
import io.camunda.connector.runtime.core.outbound.ConnectorResult.SuccessResult;
import io.camunda.connector.runtime.core.outbound.OutboundConnectorExceptionHandler;
import io.camunda.spring.client.jobhandling.CommandExceptionHandlingStrategy;
import io.camunda.spring.client.jobhandling.CommandWrapper;
import io.camunda.spring.client.metrics.MetricsRecorder;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AiAgentJobWorkerErrorHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(AiAgentJobWorkerErrorHandler.class);
  private static final int MAX_ZEEBE_COMMAND_RETRIES = 3;
  private static final int MAX_ERROR_MESSAGE_LENGTH = 6000;

  private final OutboundConnectorExceptionHandler outboundConnectorExceptionHandler;
  private final CommandExceptionHandlingStrategy exceptionHandlingStrategy;
  private final MetricsRecorder metricsRecorder;

  public AiAgentJobWorkerErrorHandler(
      final OutboundConnectorExceptionHandler outboundConnectorExceptionHandler,
      final CommandExceptionHandlingStrategy exceptionHandlingStrategy,
      final MetricsRecorder metricsRecorder) {
    this.exceptionHandlingStrategy = exceptionHandlingStrategy;
    this.metricsRecorder = metricsRecorder;
    this.outboundConnectorExceptionHandler = outboundConnectorExceptionHandler;
  }

  public void executeWithErrorHandling(
      final JobClient jobClient, final ActivatedJob job, final Supplier<AgentResponse> execution) {
    ConnectorResult result = getExecutionResult(job, execution);

    if (result.isSuccess()) {
      // no-op, job completion already handled by JobWorkerAgentRequestHandler
      return;
    }

    if (result instanceof ErrorResult errorResult) {
      LOGGER.error(
          "Exception while completing AI Agent job with key {}. Message: {}",
          job.getElementInstanceKey(),
          errorResult.exception().getMessage(),
          errorResult.exception());
      final var failCommandStep = prepareFailJobCommand(jobClient, job, errorResult);
      new CommandWrapper(
              failCommandStep,
              job,
              exceptionHandlingStrategy,
              metricsRecorder,
              MAX_ZEEBE_COMMAND_RETRIES)
          .executeAsync();
    }
  }

  private ConnectorResult getExecutionResult(
      final ActivatedJob job, final Supplier<AgentResponse> execution) {
    Duration retryBackoff = null;
    try {
      retryBackoff = getBackoffDuration(job);
      final var response = execution.get();
      return new SuccessResult(response, null);
    } catch (Exception e) {
      return outboundConnectorExceptionHandler.manageConnectorJobHandlerException(
          e, job, retryBackoff);
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

  private static FinalCommandStep<FailJobResponse> prepareFailJobCommand(
      JobClient jobClient, ActivatedJob job, ErrorResult result) {
    var retries = result.retries();
    var errorMessage = truncateErrorMessage(result.exception().getMessage());
    Duration backoff = result.retryBackoff();

    var command =
        jobClient.newFailCommand(job).retries(Math.max(retries, 0)).errorMessage(errorMessage);
    if (backoff != null) {
      command = command.retryBackoff(backoff);
    }
    if (result.responseValue() != null) {
      command = command.variables(result.responseValue());
    }
    return command;
  }

  private static String truncateErrorMessage(String message) {
    return message != null
        ? message.substring(0, Math.min(message.length(), MAX_ERROR_MESSAGE_LENGTH))
        : null;
  }
}
