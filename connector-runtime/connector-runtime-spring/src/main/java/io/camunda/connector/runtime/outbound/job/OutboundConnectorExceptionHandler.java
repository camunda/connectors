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

import static io.camunda.connector.runtime.outbound.job.SpringConnectorJobHandler.MAX_ERROR_MESSAGE_LENGTH;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.error.ConnectorRetryException;
import io.camunda.connector.api.secret.SecretContext;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.runtime.core.error.InvalidBackOffDurationException;
import io.camunda.connector.runtime.core.outbound.ConnectorResult;
import io.camunda.connector.runtime.core.secret.SecretFilter;
import io.camunda.connector.runtime.core.secret.SecretUtil;
import java.time.Duration;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OutboundConnectorExceptionHandler {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(OutboundConnectorExceptionHandler.class);
  private final SecretProvider secretProvider;

  public OutboundConnectorExceptionHandler(SecretProvider secretProvider) {
    this.secretProvider = secretProvider;
  }

  private static Map<String, Object> exceptionToMap(Exception wrappedException) {
    Map<String, Object> result = new HashMap<>();
    Throwable originalCause = wrappedException.getCause();
    result.put("type", originalCause.getClass().getName());
    var message = wrappedException.getMessage();
    if (message != null) {
      result.put(
          "message", message.substring(0, Math.min(message.length(), MAX_ERROR_MESSAGE_LENGTH)));
    }
    if (originalCause instanceof ConnectorException connectorException) {
      var code = connectorException.getErrorCode();
      var variables = connectorException.getErrorVariables();

      if (code != null) {
        result.put("code", code);
      }

      if (variables != null) {
        result.put("variables", variables);
      }
    }
    return Map.copyOf(result);
  }

  /**
   * Preserves the pre-existing three-argument overload for callers compiled against it. This
   * overload has no filter to redact secrets with, so it withholds the original message outright
   * rather than falling back to an unfiltered {@link SecretFilter#allowAll()} — the whole point of
   * the secret filter is to restrict what gets resolved, and defaulting to allow-all here would let
   * a legacy caller bypass that restriction.
   */
  public ConnectorResult.ErrorResult manageConnectorJobHandlerException(
      Exception e, ActivatedJob job, Duration retryBackoffDuration) {
    LOGGER.error(
        "Error for job: {} for tenant: {} can't be displayed: this legacy entry point has no"
            + " secret filter to redact it with.",
        job.getKey(),
        job.getTenantId());
    var wrappedException =
        new RuntimeException(
            "Original error can't be displayed: this legacy entry point has no secret filter to"
                + " redact it with, and its message might contain secrets.",
            e);
    return new ConnectorResult.ErrorResult(
        Map.of("error", exceptionToMap(wrappedException)), wrappedException, job.getRetries() - 1);
  }

  public ConnectorResult.ErrorResult manageConnectorJobHandlerException(
      Exception e, ActivatedJob job, Duration retryBackoffDuration, SecretFilter secretFilter) {
    List<String> secrets;
    try {
      var allowedKeys =
          SecretUtil.retrieveSecretKeysInInput(job.getVariables()).stream()
              .filter(secretFilter::isAllowed)
              .toList();
      secrets = this.secretProvider.fetchAll(allowedKeys, new SecretContext(job.getTenantId()));
    } catch (Exception ex) {
      LOGGER.error(
          "Initial error for job: {} for tenant: {} can't be displayed because fetching secrets failed: {}",
          job.getKey(),
          job.getTenantId(),
          ex.getMessage());
      var wrappedException =
          new RuntimeException(
              "Fetching secrets failed, original error can't be displayed as the error message might contain secrets: "
                  + ex.getMessage(),
              ex);
      return new ConnectorResult.ErrorResult(
          Map.of("error", exceptionToMap(wrappedException)),
          wrappedException,
          job.getRetries() - 1);
    }
    return switch (e) {
      case InvalidBackOffDurationException invalidBackOffDurationException ->
          handleBackOffException(invalidBackOffDurationException, secrets);
      case ConnectorRetryException connectorRetryException ->
          handleConnectorRetryException(
              job, connectorRetryException, secrets, retryBackoffDuration);
      case Exception exception ->
          handleGenericException(job, exception, secrets, retryBackoffDuration);
    };
  }

  private String hideSecretsFromMessage(String message, List<String> secrets) {
    if (!Objects.isNull(message))
      return secrets.stream()
          .reduce(message, (newMessage, nextSecret) -> newMessage.replace(nextSecret, "***"));
    else return "";
  }

  private ConnectorResult.ErrorResult handleBackOffException(Exception e, List<String> secrets) {
    Exception newException = new Exception(hideSecretsFromMessage(e.getMessage(), secrets), e);
    return new ConnectorResult.ErrorResult(
        Map.of("error", exceptionToMap(newException)), newException, 0);
  }

  private ConnectorResult.ErrorResult handleConnectorRetryException(
      ActivatedJob job, ConnectorRetryException ex, List<String> secrets, Duration retryBackoff) {
    Exception newException = new Exception(hideSecretsFromMessage(ex.getMessage(), secrets), ex);
    LOGGER.debug(
        "ConnectorRetryException while processing job: {} for tenant: {}, error message: {}",
        job.getKey(),
        job.getTenantId(),
        newException.getMessage());
    String errorCode = ex.getErrorCode();
    return handleSDKException(
        job,
        newException,
        Optional.ofNullable(ex.getRetries()).orElse(job.getRetries() - 1),
        errorCode,
        Optional.ofNullable(ex.getBackoffDuration()).orElse(retryBackoff));
  }

  private ConnectorResult.ErrorResult handleSDKException(
      ActivatedJob job, Exception ex, Integer retries, String errorCode, Duration backoffDuration) {
    LOGGER.debug(
        "Failing job with retry config => job: {} for tenant: {} with error code: {}, retries: {} and remaining backoffDuration: {}",
        job.getKey(),
        job.getTenantId(),
        errorCode,
        retries,
        backoffDuration);

    return new ConnectorResult.ErrorResult(
        Map.of("error", exceptionToMap(ex)), ex, retries, backoffDuration);
  }

  private ConnectorResult.ErrorResult handleGenericException(
      ActivatedJob job, Exception ex, List<String> secrets, Duration retryBackoff) {
    Exception newException = new Exception(hideSecretsFromMessage(ex.getMessage(), secrets), ex);
    LOGGER.debug(
        "Exception while processing job: {} for tenant: {}, message: {}",
        job.getKey(),
        job.getTenantId(),
        newException.getMessage());

    String errorCode = null;
    int retries = job.getRetries() - 1;

    if (ex instanceof ConnectorException connectorException) {
      errorCode = connectorException.getErrorCode();
    }
    if (ex instanceof ConnectorInputException || ex.getCause() instanceof ConnectorInputException) {
      retries = 0;
    }
    return handleSDKException(job, newException, retries, errorCode, retryBackoff);
  }

  /**
   * Preserves the pre-existing two-argument overload for callers compiled against it. This overload
   * has no filter to redact secrets with, so it withholds the original message outright rather than
   * falling back to an unfiltered {@link SecretFilter#allowAll()} — the whole point of the secret
   * filter is to restrict what gets resolved, and defaulting to allow-all here would let a legacy
   * caller bypass that restriction.
   */
  public ConnectorResult.ErrorResult handleFinalResultException(Exception ex, ActivatedJob job) {
    LOGGER.error(
        "Exception while processing job: {} for tenant: {}, type: {}. Its message is withheld:"
            + " this legacy entry point has no secret filter to redact it with.",
        job.getKey(),
        job.getTenantId(),
        ex.getClass().getName());
    var wrappedException =
        new RuntimeException(
            "Original error can't be displayed: this legacy entry point has no secret filter to"
                + " redact it with, and its message might contain secrets.",
            ex);
    return new ConnectorResult.ErrorResult(
        Map.of("error", exceptionToMap(wrappedException)), wrappedException, 0);
  }

  /**
   * Reports a failure raised while processing a connector's final result (its result or error
   * expression).
   *
   * <p>This must not throw: its only caller is already inside a catch block handling the failure it
   * is being told about, and an exception escaping here would leave the job neither completed nor
   * failed until its activation times out. So a secret lookup failure here (e.g. a STRICT filter
   * whose process-definition lookup fails) is caught and reported the same way an initial
   * secret-fetch failure already is in {@link #manageConnectorJobHandlerException}, rather than
   * being allowed to propagate.
   */
  public ConnectorResult.ErrorResult handleFinalResultException(
      Exception ex, ActivatedJob job, SecretFilter secretFilter) {
    List<String> secrets;
    try {
      var allowedKeys =
          SecretUtil.retrieveSecretKeysInInput(job.getVariables()).stream()
              .filter(secretFilter::isAllowed)
              .toList();
      secrets = this.secretProvider.fetchAll(allowedKeys, new SecretContext(job.getTenantId()));
    } catch (Exception fetchException) {
      LOGGER.error(
          "Exception while processing job: {} for tenant: {} can't be displayed because fetching secrets failed: {}",
          job.getKey(),
          job.getTenantId(),
          fetchException.getMessage());
      var wrappedException =
          new RuntimeException(
              "Fetching secrets failed, original error can't be displayed as the error message might contain secrets: "
                  + fetchException.getMessage(),
              fetchException);
      return new ConnectorResult.ErrorResult(
          Map.of("error", exceptionToMap(wrappedException)),
          wrappedException,
          job.getRetries() - 1);
    }
    Exception newException = new Exception(hideSecretsFromMessage(ex.getMessage(), secrets), ex);
    LOGGER.error(
        "Exception while processing job: {} for tenant: {}, message: {}",
        job.getKey(),
        job.getTenantId(),
        ex.getMessage());
    return new ConnectorResult.ErrorResult(
        Map.of("error", exceptionToMap(newException)), newException, 0);
  }
}
