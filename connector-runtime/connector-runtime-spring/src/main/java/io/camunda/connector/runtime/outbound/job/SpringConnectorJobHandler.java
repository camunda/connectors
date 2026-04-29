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

import static java.util.Objects.requireNonNullElse;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.api.command.JobCallbackFinalCommandStep;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.CompleteJobResponse;
import io.camunda.client.api.response.FailJobResponse;
import io.camunda.client.api.response.ThrowErrorResponse;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.api.worker.JobHandler;
import io.camunda.client.jobhandling.CommandOutcome;
import io.camunda.client.jobhandling.JobCallbackCommandWrapperFactory;
import io.camunda.client.metrics.MetricsRecorder;
import io.camunda.client.metrics.MetricsRecorder.CounterMetricsContext;
import io.camunda.client.metrics.MetricsRecorder.TimerMetricsContext;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.outbound.ConnectorResponse;
import io.camunda.connector.api.outbound.ConnectorResponse.AdHocSubProcessConnectorResponse;
import io.camunda.connector.api.outbound.ConnectorResponse.AdHocSubProcessConnectorResponse.ElementActivation;
import io.camunda.connector.api.outbound.ConnectorResponse.StandardConnectorResponse;
import io.camunda.connector.api.outbound.JobCompletionFailure;
import io.camunda.connector.api.outbound.JobCompletionFailure.BpmnErrorThrown;
import io.camunda.connector.api.outbound.JobCompletionFailure.CommandFailure;
import io.camunda.connector.api.outbound.JobCompletionFailure.ExecutionFailed;
import io.camunda.connector.api.outbound.JobCompletionFailure.JobErrorRaised;
import io.camunda.connector.api.outbound.JobCompletionListener;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.runtime.core.ConnectorResultHandler;
import io.camunda.connector.runtime.core.Keywords;
import io.camunda.connector.runtime.core.error.BpmnError;
import io.camunda.connector.runtime.core.error.ConnectorError;
import io.camunda.connector.runtime.core.error.IgnoreError;
import io.camunda.connector.runtime.core.error.InvalidBackOffDurationException;
import io.camunda.connector.runtime.core.error.JobError;
import io.camunda.connector.runtime.core.outbound.ConnectorResult;
import io.camunda.connector.runtime.core.outbound.ErrorExpressionJobContext;
import io.camunda.connector.runtime.core.outbound.JobHandlerContext;
import io.camunda.connector.runtime.core.secret.SecretProviderAggregator;
import io.camunda.connector.runtime.core.secret.SecretProviderDiscovery;
import io.camunda.connector.runtime.metrics.ConnectorMetrics;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
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
  private final OutboundConnectorFunction call;
  private final JobCallbackCommandWrapperFactory jobCallbackCommandWrapperFactory;
  private final MetricsRecorder connectorsOutboundMetrics;
  private final OutboundConnectorExceptionHandler outboundConnectorExceptionHandler;
  private final ConnectorResultHandler connectorResultHandler;
  private final SecretProvider secretProvider;
  private final ValidationProvider validationProvider;
  private final DocumentFactory documentFactory;
  private final ObjectMapper objectMapper;

  public SpringConnectorJobHandler(
      MetricsRecorder outboundMetrics,
      JobCallbackCommandWrapperFactory jobCallbackCommandWrapperFactory,
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
    this.jobCallbackCommandWrapperFactory = jobCallbackCommandWrapperFactory;
    this.connectorsOutboundMetrics = outboundMetrics;
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
  public void handle(JobClient client, ActivatedJob job) throws Exception {
    CounterMetricsContext counterMetricsContext = ConnectorMetrics.counter(job);
    TimerMetricsContext timerMetricsContext = ConnectorMetrics.timer(job);
    connectorsOutboundMetrics.increaseActivated(counterMetricsContext);
    connectorsOutboundMetrics.executeWithTimer(
        timerMetricsContext,
        () -> {
          this.executeJob(client, job, counterMetricsContext);
          return null;
        });
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
    var context =
        new JobHandlerContext(
            job, getSecretProvider(), validationProvider, documentFactory, objectMapper);
    ConnectorResult result = getConnectorResult(job, context);
    processFinalResult(client, job, context, result, counterMetricsContext);
  }

  private ConnectorResult getConnectorResult(ActivatedJob job, OutboundConnectorContext context) {
    Duration retryBackoff = null;
    try {
      retryBackoff = getBackoffDuration(job);

      var connectorResponse = getConnectorResponse(context);

      if (connectorResponse instanceof AdHocSubProcessConnectorResponse) {
        // AHSP responses provide their own variables; skip result expression evaluation
        return new ConnectorResult.SuccessResult(connectorResponse, Map.of());
      }

      var responseVariables =
          connectorResultHandler.createOutputVariables(
              connectorResponse.responseValue(),
              job.getCustomHeaders().get(Keywords.RESULT_VARIABLE_KEYWORD),
              job.getCustomHeaders().get(Keywords.RESULT_EXPRESSION_KEYWORD));
      return new ConnectorResult.SuccessResult(connectorResponse, responseVariables);
    } catch (Exception e) {
      return outboundConnectorExceptionHandler.manageConnectorJobHandlerException(
          e, job, retryBackoff);
    }
  }

  private ConnectorResponse getConnectorResponse(OutboundConnectorContext context)
      throws Exception {
    final var responseValue = call.execute(context);
    if (responseValue instanceof ConnectorResponse connectorResponse) {
      return connectorResponse;
    }

    return StandardConnectorResponse.of(responseValue);
  }

  private void processFinalResult(
      JobClient client,
      ActivatedJob job,
      OutboundConnectorContext context,
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
          error ->
              handleConnectorError(client, job, context, finalResult, error, counterMetricsContext),
          () -> handleFinalResult(client, job, context, finalResult, counterMetricsContext));
    } catch (Exception ex) {
      var failJobFuture =
          failJob(
              client,
              job,
              this.outboundConnectorExceptionHandler.handleFinalResultException(ex, job),
              counterMetricsContext);
      notifyFailureOnCommandOutcome(
          failJobFuture,
          context,
          connectorResponseOrNull(finalResult),
          completionFailure -> new ExecutionFailed(ex, completionFailure));
    }
  }

  private void handleFinalResult(
      JobClient jobClient,
      ActivatedJob job,
      OutboundConnectorContext context,
      ConnectorResult finalResult,
      CounterMetricsContext counterMetricsContext) {
    if (finalResult instanceof ConnectorResult.SuccessResult successResult) {
      LOGGER.info("Completing job: {} for tenant: {}", job.getKey(), job.getTenantId());
      completeJob(jobClient, job, context, successResult, counterMetricsContext);
    } else if (finalResult instanceof ConnectorResult.ErrorResult errorResult) {
      // Handle Java error, e.g. ConnectorException
      // these errors won't be handled ConnectorHelper.examineErrorExpression
      LOGGER.error(
          "Exception while completing job: {}, message: {}",
          JobForLog.from(job),
          errorResult.exception().getMessage(),
          errorResult.exception());

      // pre-response failure path: function threw before returning a response, so notify with a
      // null response (subscribers to JobCompletionListener can still react)
      var failJobFuture = failJob(jobClient, job, errorResult, counterMetricsContext);
      notifyFailureOnCommandOutcome(
          failJobFuture,
          context,
          null,
          completionFailure -> new ExecutionFailed(errorResult.exception(), completionFailure));
    }
  }

  private void handleConnectorError(
      JobClient client,
      ActivatedJob job,
      OutboundConnectorContext context,
      ConnectorResult finalResult,
      ConnectorError error,
      CounterMetricsContext counterMetricsContext) {
    var response = connectorResponseOrNull(finalResult);

    switch (error) {
      case BpmnError bpmnError -> {
        LOGGER.debug(
            "Throwing BPMN error for job {} with code {}", job.getKey(), bpmnError.errorCode());
        var bpmnErrorFuture = throwBpmnError(client, job, bpmnError, counterMetricsContext);
        notifyFailureOnCommandOutcome(
            bpmnErrorFuture,
            context,
            response,
            completionFailure ->
                new BpmnErrorThrown(
                    bpmnError.errorCode(),
                    bpmnError.errorMessage(),
                    bpmnError.variables(),
                    completionFailure));
      }
      case JobError jobError -> {
        LOGGER.debug("Throwing incident for job {}", job.getKey());
        var failJobFuture =
            failJob(
                client,
                job,
                new ConnectorResult.ErrorResult(
                    jobError.variablesWithErrorMessage(),
                    new RuntimeException(jobError.errorMessage()),
                    jobError.retries(),
                    jobError.retryBackoff()),
                counterMetricsContext);
        notifyFailureOnCommandOutcome(
            failJobFuture,
            context,
            response,
            completionFailure ->
                new JobErrorRaised(
                    jobError.errorMessage(),
                    jobError.variablesWithErrorMessage(),
                    completionFailure));
      }
      case IgnoreError ignoreError ->
          handleIgnoreError(
              client, job, context, finalResult, response, ignoreError, counterMetricsContext);
    }
  }

  private void handleIgnoreError(
      JobClient client,
      ActivatedJob job,
      OutboundConnectorContext context,
      ConnectorResult finalResult,
      ConnectorResponse response,
      IgnoreError ignoreError,
      CounterMetricsContext counterMetricsContext) {
    if (finalResult instanceof ConnectorResult.SuccessResult successResult
        && successResult.connectorResponse() instanceof AdHocSubProcessConnectorResponse) {
      LOGGER.debug(
          "IgnoreError not supported for AdHocSubProcessConnectorResponse, job {}", job.getKey());
      var cause =
          new UnsupportedOperationException("IgnoreError is not supported for this connector");
      var failJobFuture =
          failJob(
              client,
              job,
              new ConnectorResult.ErrorResult(ignoreError.variables(), cause, 0, null),
              counterMetricsContext);

      notifyFailureOnCommandOutcome(
          failJobFuture,
          context,
          response,
          completionFailure -> new ExecutionFailed(cause, completionFailure));
    } else {
      LOGGER.debug("Ignoring error for job {}", job.getKey());
      completeJob(
          client,
          job,
          context,
          new ConnectorResult.SuccessResult(
              StandardConnectorResponse.of(null), ignoreError.variables()),
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

  private CompletableFuture<CommandOutcome> failJob(
      JobClient client,
      ActivatedJob job,
      ConnectorResult.ErrorResult result,
      CounterMetricsContext counterMetricsContext) {
    final var command = prepareFailJobCommand(client, job, result);
    return jobCallbackCommandWrapperFactory
        .create(command, job.getDeadline(), counterMetricsContext, MAX_ZEEBE_COMMAND_RETRIES)
        .executeAsync();
  }

  private static JobCallbackFinalCommandStep<FailJobResponse> prepareFailJobCommand(
      JobClient client, ActivatedJob job, ConnectorResult.ErrorResult result) {
    var retries = result.retries();
    var baseMessage = result.exception().getMessage();
    var errorMessage =
        truncateErrorMessage(
            baseMessage
                + (result.responseValue() != null
                    ? " | Error variables: " + result.responseValue()
                    : ""));
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

  private CompletableFuture<CommandOutcome> throwBpmnError(
      JobClient client,
      ActivatedJob job,
      BpmnError value,
      CounterMetricsContext counterMetricsContext) {
    final var command = prepareThrowBpmnErrorCommand(client, job, value);
    return jobCallbackCommandWrapperFactory
        .create(command, job.getDeadline(), counterMetricsContext, MAX_ZEEBE_COMMAND_RETRIES)
        .executeAsync();
  }

  private static JobCallbackFinalCommandStep<ThrowErrorResponse> prepareThrowBpmnErrorCommand(
      JobClient client, ActivatedJob job, BpmnError error) {
    var command =
        client.newThrowErrorCommand(job).errorCode(error.errorCode()).variables(error.variables());
    var errorMessage = truncateErrorMessage(error.errorMessage());
    if (errorMessage != null) {
      command = command.errorMessage(errorMessage);
    }
    return command;
  }

  private CompletableFuture<CommandOutcome> completeJob(
      JobClient client,
      ActivatedJob job,
      OutboundConnectorContext context,
      ConnectorResult.SuccessResult result,
      CounterMetricsContext counterMetricsContext) {
    ConnectorResponse connectorResponse = result.connectorResponse();

    final var command =
        switch (connectorResponse) {
          case StandardConnectorResponse ignored -> prepareCompleteJobCommand(client, job, result);
          case AdHocSubProcessConnectorResponse ahsp ->
              prepareAdHocSubProcessCompleteJobCommand(client, job, ahsp);
        };

    var completeJobFuture =
        jobCallbackCommandWrapperFactory
            .create(command, job.getDeadline(), counterMetricsContext, MAX_ZEEBE_COMMAND_RETRIES)
            .executeAsync();

    completeJobFuture.whenComplete(
        (outcome, throwable) -> {
          var commandFailure = commandFailureOrNull(outcome, throwable);
          if (commandFailure == null) {
            notifyJobCompleted(context, connectorResponse);
          } else {
            notifyJobCompletionFailed(context, connectorResponse, commandFailure);
          }
        });

    return completeJobFuture;
  }

  private static JobCallbackFinalCommandStep<CompleteJobResponse> prepareCompleteJobCommand(
      JobClient client, ActivatedJob job, ConnectorResult.SuccessResult result) {
    return client.newCompleteCommand(job).variables(result.variables());
  }

  private static JobCallbackFinalCommandStep<CompleteJobResponse>
      prepareAdHocSubProcessCompleteJobCommand(
          JobClient client, ActivatedJob job, AdHocSubProcessConnectorResponse connectorResponse) {
    Map<String, Object> variables = requireNonNullElse(connectorResponse.variables(), Map.of());
    return client
        .newCompleteCommand(job)
        .variables(variables)
        .withResult(
            resultStep -> {
              var adHocSubProcess =
                  resultStep
                      .forAdHocSubProcess()
                      .completionConditionFulfilled(
                          connectorResponse.completionConditionFulfilled())
                      .cancelRemainingInstances(connectorResponse.cancelRemainingInstances());

              List<ElementActivation> elementActivations =
                  requireNonNullElse(connectorResponse.elementActivations(), List.of());
              for (ElementActivation activation : elementActivations) {
                adHocSubProcess =
                    adHocSubProcess
                        .activateElement(activation.elementId())
                        .variables(requireNonNullElse(activation.variables(), Map.of()));
              }

              return adHocSubProcess;
            });
  }

  private static String truncateErrorMessage(String message) {
    return message != null
        ? message.substring(0, Math.min(message.length(), MAX_ERROR_MESSAGE_LENGTH))
        : null;
  }

  /**
   * Dispatches a {@link #notifyJobCompletionFailed} once a Zeebe command future resolves, building
   * the failure via {@code failureBuilder} from the resolved {@link CommandFailure} (which is
   * {@code null} when the command was accepted).
   *
   * <p>Used by paths that always end in failure regardless of the command outcome (BPMN error, job
   * error, IgnoreError misuse) — the builder produces the appropriate {@link JobCompletionFailure}
   * subtype and embeds the command outcome where applicable.
   */
  private void notifyFailureOnCommandOutcome(
      CompletableFuture<CommandOutcome> future,
      OutboundConnectorContext context,
      ConnectorResponse response,
      Function<CommandFailure, JobCompletionFailure> failureBuilder) {
    future.whenComplete(
        (outcome, throwable) ->
            notifyJobCompletionFailed(
                context, response, failureBuilder.apply(commandFailureOrNull(outcome, throwable))));
  }

  private void notifyJobCompleted(OutboundConnectorContext context, ConnectorResponse response) {
    if (!(call instanceof JobCompletionListener listener)) {
      return;
    }

    try {
      listener.onJobCompleted(context, response);
    } catch (Exception e) {
      LOGGER.warn("JobCompletionListener callback failed", e);
    }
  }

  private void notifyJobCompletionFailed(
      OutboundConnectorContext context, ConnectorResponse response, JobCompletionFailure failure) {
    if (!(call instanceof JobCompletionListener listener)) {
      return;
    }

    try {
      listener.onJobCompletionFailed(context, response, failure);
    } catch (Exception e) {
      LOGGER.warn("JobCompletionListener callback failed", e);
    }
  }

  private static ConnectorResponse connectorResponseOrNull(ConnectorResult result) {
    return result instanceof ConnectorResult.SuccessResult successResult
        ? successResult.connectorResponse()
        : null;
  }

  /**
   * Translates an internal {@link CommandOutcome} into the SDK-level {@link CommandFailure}.
   * Returns {@code null} for a successful outcome so it can be embedded directly into the optional
   * {@code commandFailure} field of {@link ExecutionFailed}, {@link BpmnErrorThrown}, or {@link
   * JobErrorRaised}.
   */
  private static CommandFailure commandFailureOrNull(CommandOutcome outcome, Throwable throwable) {
    if (throwable != null) {
      return new CommandFailure.CommandFailed(throwable);
    }

    return switch (outcome) {
      case CommandOutcome.Completed c -> null;
      case CommandOutcome.Failed f -> new CommandFailure.CommandFailed(f.cause());
      case CommandOutcome.Ignored i -> new CommandFailure.CommandIgnored(i.cause());
    };
  }
}
