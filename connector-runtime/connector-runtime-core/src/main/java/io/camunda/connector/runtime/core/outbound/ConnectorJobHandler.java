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

package io.camunda.connector.runtime.core.outbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.CompleteJobResponse;
import io.camunda.client.api.response.FailJobResponse;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.api.worker.JobHandler;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.runtime.core.ConnectorHelper;
import io.camunda.connector.runtime.core.Keywords;
import io.camunda.connector.runtime.core.error.BpmnError;
import io.camunda.connector.runtime.core.error.ConnectorError;
import io.camunda.connector.runtime.core.error.InvalidBackOffDurationException;
import io.camunda.connector.runtime.core.error.JobError;
import io.camunda.connector.runtime.core.outbound.ConnectorResult.ErrorResult;
import io.camunda.connector.runtime.core.outbound.ConnectorResult.SuccessResult;
import io.camunda.connector.runtime.core.outbound.ErrorExpressionJobContext.ErrorExpressionJob;
import io.camunda.connector.runtime.core.secret.SecretProviderAggregator;
import io.camunda.connector.runtime.core.secret.SecretProviderDiscovery;
import io.camunda.document.factory.DocumentFactory;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** {@link JobHandler} that wraps an {@link OutboundConnectorFunction} */
public class ConnectorJobHandler implements JobHandler {

  // Protects Zeebe from enormously large messages it cannot handle
  public static final int MAX_ERROR_MESSAGE_LENGTH = 6000;
  private static final Logger LOGGER = LoggerFactory.getLogger(ConnectorJobHandler.class);
  protected final OutboundConnectorFunction call;
  protected SecretProvider secretProvider;

  protected ValidationProvider validationProvider;

  protected DocumentFactory documentFactory;

  protected ObjectMapper objectMapper;

  private OutboundConnectorExceptionHandler outboundConnectorExceptionHandler;

  /**
   * Create a handler wrapper for the specified connector function.
   *
   * @param call - the connector function to call
   */
  public ConnectorJobHandler(
      final OutboundConnectorFunction call,
      final SecretProvider secretProvider,
      final ValidationProvider validationProvider,
      final DocumentFactory documentFactory,
      final ObjectMapper objectMapper) {
    this.call = call;
    this.secretProvider = secretProvider;
    this.validationProvider = validationProvider;
    this.documentFactory = documentFactory;
    this.objectMapper = objectMapper;
    this.outboundConnectorExceptionHandler =
        new OutboundConnectorExceptionHandler(getSecretProvider());
  }

  protected static FinalCommandStep<CompleteJobResponse> prepareCompleteJobCommand(
      JobClient client, ActivatedJob job, SuccessResult result) {
    return client.newCompleteCommand(job).variables(result.variables());
  }

  protected static FinalCommandStep<FailJobResponse> prepareFailJobCommand(
      JobClient client, ActivatedJob job, ErrorResult result) {
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

  protected static FinalCommandStep<Void> prepareThrowBpmnErrorCommand(
      JobClient client, ActivatedJob job, BpmnError error) {
    return client
        .newThrowErrorCommand(job)
        .errorCode(error.code())
        .variables(error.variables())
        .errorMessage(truncateErrorMessage(error.message()));
  }

  private static String truncateErrorMessage(String message) {
    return message != null
        ? message.substring(0, Math.min(message.length(), MAX_ERROR_MESSAGE_LENGTH))
        : null;
  }

  @Override
  public void handle(final JobClient client, final ActivatedJob job) {
    LOGGER.info(
        "Received job: {} of type: {} for tenant: {}",
        job.getKey(),
        job.getType(),
        job.getTenantId());
    ConnectorResult result = getConnectorResult(job);
    processFinalResult(client, job, result);
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
          ConnectorHelper.createOutputVariables(
              response,
              job.getCustomHeaders().get(Keywords.RESULT_VARIABLE_KEYWORD),
              job.getCustomHeaders().get(Keywords.RESULT_EXPRESSION_KEYWORD));
      return new SuccessResult(response, responseVariables);
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

  private void processFinalResult(JobClient client, ActivatedJob job, ConnectorResult finalResult) {
    try {
      Optional<ConnectorError> optionalConnectorError =
          ConnectorHelper.examineErrorExpression(
              finalResult.responseValue(),
              job.getCustomHeaders(),
              new ErrorExpressionJobContext(new ErrorExpressionJob(job.getRetries())));
      optionalConnectorError.ifPresentOrElse(
          error -> handleBPMNError(client, job, error),
          () -> handleSuccessResult(client, job, finalResult));
    } catch (Exception ex) {
      failJob(
          client, job, this.outboundConnectorExceptionHandler.handleFinalResultException(ex, job));
    }
  }

  private void handleSuccessResult(
      JobClient jobClient, ActivatedJob job, ConnectorResult finalResult) {
    if (finalResult instanceof SuccessResult successResult) {
      LOGGER.debug("Completing job: {} for tenant: {}", job.getKey(), job.getTenantId());
      completeJob(jobClient, job, successResult);
    } else if (finalResult instanceof ErrorResult errorResult) {
      // Handle Java error, e.g. ConnectorException
      // these errors won't be handled ConnectorHelper.examineErrorExpression
      LOGGER.error(
          "Exception while completing job: {}, message: {}",
          JobForLog.from(job),
          errorResult.exception().getMessage(),
          errorResult.exception());
      failJob(jobClient, job, errorResult);
    }
  }

  private void handleBPMNError(JobClient client, ActivatedJob job, ConnectorError error) {
    if (error instanceof BpmnError bpmnError) {
      LOGGER.debug("Throwing BPMN error for job {} with code {}", job.getKey(), bpmnError.code());
      throwBpmnError(client, job, bpmnError);
    } else if (error instanceof JobError jobError) {
      LOGGER.debug("Throwing incident for job {}", job.getKey());
      failJob(
          client,
          job,
          new ErrorResult(
              Map.of("error", jobError.message()),
              new RuntimeException(jobError.message()),
              jobError.retries(),
              jobError.retryBackoff()));
    }
  }

  protected SecretProvider getSecretProvider() {
    // if custom provider / aggregator is provided by the runtime, use it
    if (secretProvider != null) {
      return secretProvider;
    }
    // otherwise fall back to default implementation (SPI discovery)
    return new SecretProviderAggregator(SecretProviderDiscovery.discoverSecretProviders());
  }

  protected void completeJob(JobClient client, ActivatedJob job, SuccessResult result) {
    prepareCompleteJobCommand(client, job, result).send().join();
  }

  protected void failJob(JobClient client, ActivatedJob job, ErrorResult result) {
    prepareFailJobCommand(client, job, result).send().join();
  }

  protected void throwBpmnError(JobClient client, ActivatedJob job, BpmnError value) {
    prepareThrowBpmnErrorCommand(client, job, value).send().join();
  }

  record JobForLog(
      Long key,
      Map<String, String> customHeaders,
      String tenantId,
      String bpmnProcessId,
      String type,
      Long processDefinitionKey,
      Integer processDefinitionVersion,
      Long processInstanceKey) {
    public static JobForLog from(ActivatedJob job) {
      return new JobForLog(
          job.getKey(),
          job.getCustomHeaders(),
          job.getTenantId(),
          job.getBpmnProcessId(),
          job.getType(),
          job.getProcessDefinitionKey(),
          job.getProcessDefinitionVersion(),
          job.getProcessInstanceKey());
    }
  }
}
