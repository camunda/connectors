/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.jobworker;

import io.camunda.client.api.command.CompleteJobCommandStep1;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.command.ThrowErrorCommandStep1.ThrowErrorCommandStep2;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.FailJobResponse;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.jobhandling.CommandExceptionHandlingStrategy;
import io.camunda.client.jobhandling.CommandWrapper;
import io.camunda.client.metrics.DefaultNoopMetricsRecorder;
import io.camunda.client.metrics.MetricsRecorder;
import io.camunda.connector.agenticai.aiagent.AiAgentJobWorker;
import io.camunda.connector.agenticai.aiagent.agent.JobWorkerAgentRequestHandler;
import io.camunda.connector.agenticai.aiagent.jobworker.JobWorkerAgentResult.AgentErrorResult;
import io.camunda.connector.agenticai.aiagent.jobworker.JobWorkerAgentResult.AgentSuccessResult;
import io.camunda.connector.agenticai.aiagent.model.JobWorkerAgentCompletion;
import io.camunda.connector.agenticai.model.tool.ToolCallProcessVariable;
import io.camunda.connector.runtime.core.ConnectorResultHandler;
import io.camunda.connector.runtime.core.Keywords;
import io.camunda.connector.runtime.core.error.BpmnError;
import io.camunda.connector.runtime.core.error.ConnectorError;
import io.camunda.connector.runtime.core.error.InvalidBackOffDurationException;
import io.camunda.connector.runtime.core.error.JobError;
import io.camunda.connector.runtime.core.outbound.ConnectorResult.ErrorResult;
import io.camunda.connector.runtime.core.outbound.ErrorExpressionJobContext;
import io.camunda.connector.runtime.core.outbound.ErrorExpressionJobContext.ErrorExpressionJob;
import io.camunda.connector.runtime.core.secret.SecretFilter;
import io.camunda.connector.runtime.core.secret.SecretFilterFactory;
import io.camunda.connector.runtime.core.secret.SecretFilterFactory.SecretFilterContext;
import io.camunda.connector.runtime.metrics.ConnectorsOutboundMetrics;
import io.camunda.connector.runtime.outbound.job.OutboundConnectorExceptionHandler;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AiAgentJobWorkerHandlerImpl implements AiAgentJobWorkerHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(AiAgentJobWorkerHandlerImpl.class);
  private static final int MAX_ZEEBE_COMMAND_RETRIES = 3;
  private static final int MAX_ERROR_MESSAGE_LENGTH = 6000;

  private final JobWorkerAgentExecutionContextFactory executionContextFactory;
  private final JobWorkerAgentRequestHandler agentRequestHandler;
  private final OutboundConnectorExceptionHandler outboundConnectorExceptionHandler;
  private final ConnectorResultHandler connectorResultHandler;
  private final CommandExceptionHandlingStrategy exceptionHandlingStrategy;
  private final ConnectorsOutboundMetrics connectorsOutboundMetrics;
  private final MetricsRecorder metricsRecorder = new DefaultNoopMetricsRecorder();
  private final SecretFilterFactory secretFilterFactory;

  public AiAgentJobWorkerHandlerImpl(
      final JobWorkerAgentExecutionContextFactory executionContextFactory,
      final JobWorkerAgentRequestHandler agentRequestHandler,
      final CommandExceptionHandlingStrategy exceptionHandlingStrategy,
      final OutboundConnectorExceptionHandler outboundConnectorExceptionHandler,
      final ConnectorResultHandler connectorResultHandler,
      final ConnectorsOutboundMetrics connectorsOutboundMetrics,
      SecretFilterFactory secretFilterFactory) {
    this.executionContextFactory = executionContextFactory;
    this.agentRequestHandler = agentRequestHandler;
    this.exceptionHandlingStrategy = exceptionHandlingStrategy;
    this.outboundConnectorExceptionHandler = outboundConnectorExceptionHandler;
    this.connectorResultHandler = connectorResultHandler;
    this.connectorsOutboundMetrics = connectorsOutboundMetrics;
    this.secretFilterFactory = secretFilterFactory;
  }

  @Override
  public void handle(final JobClient jobClient, final ActivatedJob job) {
    connectorsOutboundMetrics.increaseInvocation(job);
    connectorsOutboundMetrics.executeWithTimer(job, () -> this.executeJob(jobClient, job));
  }

  private void executeJob(final JobClient jobClient, final ActivatedJob job) {
    final SecretFilter secretFilter =
        secretFilterFactory.create(
            new SecretFilterContext(job.getProcessDefinitionKey(), job.getElementId()));
    // kept for the error paths: a secret rotated since the input was bound no longer reads back,
    // and the value an error message carries is the one substituted then
    final List<String> capturedSecrets = new ArrayList<>();
    final var agentResult = getAgentResult(jobClient, job, secretFilter, capturedSecrets);

    try {
      Optional<ConnectorError> optionalConnectorError =
          connectorResultHandler.examineErrorExpression(
              agentResult.responseValue(),
              job.getCustomHeaders(),
              new ErrorExpressionJobContext(new ErrorExpressionJob(job.getRetries())));

      optionalConnectorError.ifPresentOrElse(
          connectorError ->
              handleConnectorError(jobClient, job, connectorError, secretFilter, capturedSecrets),
          () -> {
            switch (agentResult) {
              case AgentSuccessResult successResult ->
                  completeJob(jobClient, job, successResult.completion());
              case AgentErrorResult errorResult ->
                  failJob(jobClient, job, errorResult.errorResult());
            }
          });
    } catch (Exception e) {
      failJob(
          jobClient,
          job,
          this.outboundConnectorExceptionHandler.handleFinalResultException(
              e, job, secretFilter, List.copyOf(capturedSecrets)));
    }
  }

  private JobWorkerAgentResult getAgentResult(
      final JobClient jobClient,
      final ActivatedJob job,
      final SecretFilter secretFilter,
      final List<String> capturedSecrets) {
    Duration retryBackoff = null;
    try {
      retryBackoff = getBackoffDuration(job);

      final var executionContext =
          executionContextFactory.createExecutionContext(
              jobClient, job, secretFilter, capturedSecrets::addAll);
      final var completion = agentRequestHandler.handleRequest(executionContext);

      return new AgentSuccessResult(completion);
    } catch (Exception e) {
      final var errorResult =
          outboundConnectorExceptionHandler.manageConnectorJobHandlerException(
              e, job, retryBackoff, secretFilter, List.copyOf(capturedSecrets));
      return new AgentErrorResult(errorResult);
    }
  }

  private void handleConnectorError(
      final JobClient jobClient,
      final ActivatedJob job,
      final ConnectorError rawError,
      final SecretFilter secretFilter,
      final List<String> capturedSecrets) {
    // an error expression builds its message from the response, which can carry a resolved secret,
    // so it is redacted before the Zeebe command is built from it
    final var connectorError =
        outboundConnectorExceptionHandler.maskConnectorError(
            rawError, job, secretFilter, List.copyOf(capturedSecrets));
    switch (connectorError) {
      case BpmnError bpmnError -> throwBpmnError(jobClient, job, bpmnError);
      case JobError jobError ->
          failJob(
              jobClient,
              job,
              new ErrorResult(
                  Map.of("error", jobError.errorMessage()),
                  new RuntimeException(jobError.errorMessage()),
                  jobError.retries(),
                  jobError.retryBackoff()));
    }
  }

  private void completeJob(
      final JobClient jobClient,
      final ActivatedJob job,
      final JobWorkerAgentCompletion completion) {
    LOGGER.debug(
        "Asynchronously executing complete command for job: {}, max retries: {}",
        job.getKey(),
        MAX_ZEEBE_COMMAND_RETRIES);

    try {
      connectorsOutboundMetrics.increaseCompletion(job);
    } finally {
      final var completeCommand = prepareCompleteCommand(jobClient, job, completion);
      executeCommandAsync(
          completeCommand,
          job,
          (command, throwable) -> {
            completion.onCompletionError(throwable);
            exceptionHandlingStrategy.handleCommandError(command, throwable);
          });
    }
  }

  private CompleteJobCommandStep1 prepareCompleteCommand(
      final JobClient jobClient,
      final ActivatedJob job,
      final JobWorkerAgentCompletion completion) {
    return jobClient
        .newCompleteCommand(job)
        .variables(completion.variables())
        .withResult(
            result -> {
              var adHocSubProcess =
                  result
                      .forAdHocSubProcess()
                      .completionConditionFulfilled(completion.completionConditionFulfilled())
                      .cancelRemainingInstances(completion.cancelRemainingInstances());

              if (completion.agentResponse() != null) {
                final var agentResponse = completion.agentResponse();
                for (ToolCallProcessVariable toolCall : agentResponse.toolCalls()) {
                  if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Activating tool {}: {}", toolCall.metadata().name(), toolCall);
                  } else {
                    LOGGER.debug("Activating tool {}", toolCall.metadata().name());
                  }

                  adHocSubProcess =
                      adHocSubProcess
                          .activateElement(toolCall.metadata().name())
                          .variables(
                              Map.ofEntries(
                                  Map.entry(AiAgentJobWorker.TOOL_CALL_VARIABLE, toolCall),
                                  // Creating empty toolCallResult variable to avoid variable
                                  // to bubble up in the upper scopes while merging variables on
                                  // ad-hoc sub-process inner instance completion.
                                  Map.entry(AiAgentJobWorker.TOOL_CALL_RESULT_VARIABLE, "")));
                }
              }

              return adHocSubProcess;
            });
  }

  private void failJob(
      final JobClient jobClient, final ActivatedJob job, final ErrorResult errorResult) {
    // the message is redacted, the exception it was built from is not: logging the throwable would
    // print the original cause, secrets and all
    LOGGER.error(
        "Exception while completing AI Agent job with key {}: {}",
        job.getKey(),
        errorResult.exception().getMessage());

    try {
      connectorsOutboundMetrics.increaseFailure(job);
    } finally {
      final var failCommand = prepareFailJobCommand(jobClient, job, errorResult);
      executeCommandAsync(failCommand, job);
    }
  }

  private FinalCommandStep<FailJobResponse> prepareFailJobCommand(
      final JobClient jobClient, final ActivatedJob job, final ErrorResult result) {
    final var retries = result.retries();
    final var errorMessage = truncateErrorMessage(result.exception().getMessage());
    final var retryBackoff = result.retryBackoff();

    var command =
        jobClient.newFailCommand(job).retries(Math.max(retries, 0)).errorMessage(errorMessage);
    if (retryBackoff != null) {
      command = command.retryBackoff(retryBackoff);
    }
    if (result.responseValue() != null) {
      command = command.variables(result.responseValue());
    }
    return command;
  }

  private void throwBpmnError(
      final JobClient jobClient, final ActivatedJob job, final BpmnError bpmnError) {
    // the code is not redacted, so it stays out of the log: unlike the command and the error
    // variables that carry it, pod logs are shipped to systems with their own access controls
    LOGGER.error(
        "BPMN error while completing AI Agent job with key {}. Message: {}",
        job.getKey(),
        bpmnError.errorMessage());

    try {
      connectorsOutboundMetrics.increaseBpmnError(job);
    } finally {
      final var throwBpmnErrorCommand = prepareThrowBpmnErrorCommand(jobClient, job, bpmnError);
      executeCommandAsync(throwBpmnErrorCommand, job);
    }
  }

  private ThrowErrorCommandStep2 prepareThrowBpmnErrorCommand(
      final JobClient jobClient, final ActivatedJob job, final BpmnError bpmnError) {
    return jobClient
        .newThrowErrorCommand(job)
        .errorCode(bpmnError.errorCode())
        .variables(bpmnError.variables())
        .errorMessage(truncateErrorMessage(bpmnError.errorMessage()));
  }

  private void executeCommandAsync(final FinalCommandStep<?> command, final ActivatedJob job) {
    executeCommandAsync(command, job, this.exceptionHandlingStrategy);
  }

  private void executeCommandAsync(
      final FinalCommandStep<?> command,
      final ActivatedJob job,
      final CommandExceptionHandlingStrategy exceptionHandlingStrategy) {
    final var commandWrapper =
        new CommandWrapper(
            command, job, exceptionHandlingStrategy, metricsRecorder, MAX_ZEEBE_COMMAND_RETRIES);
    commandWrapper.executeAsync();
  }

  private Duration getBackoffDuration(final ActivatedJob job) {
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

  private String truncateErrorMessage(final String message) {
    return message != null
        ? message.substring(0, Math.min(message.length(), MAX_ERROR_MESSAGE_LENGTH))
        : null;
  }
}
