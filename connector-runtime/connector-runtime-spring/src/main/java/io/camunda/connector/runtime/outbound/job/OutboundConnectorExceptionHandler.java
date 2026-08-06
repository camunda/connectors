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
import io.camunda.connector.runtime.core.error.InvalidJobTimeoutException;
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

  /** Stands in for a container that (transitively) contains itself, so masking cannot loop. */
  private static final String CIRCULAR_REFERENCE = "[circular reference]";

  private final SecretProvider secretProvider;

  public OutboundConnectorExceptionHandler(SecretProvider secretProvider) {
    this.secretProvider = secretProvider;
  }

  private static Map<String, Object> exceptionToMap(
      Exception wrappedException, List<String> secrets) {
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
        // deliberately not masked: BPMN error boundary events match on this value, so altering it
        // would stop the error from being caught
        result.put("code", code);
      }

      if (variables != null) {
        result.put("variables", maskSecrets(variables, secrets));
      }
    }
    return Map.copyOf(result);
  }

  /**
   * Masks every secret occurrence in {@code value}, recursing through maps and collections.
   *
   * <p>The error message is masked by the callers, but the error variables are copied straight off
   * the original exception, and for HTTP connectors they carry the full response body and headers
   * (see {@code ConnectorExceptionMapper}). An API that echoes a rejected credential back would
   * otherwise put the resolved secret into process variables, which are visible to anyone who can
   * see the process instance.
   *
   * <p>Only strings are rewritten; numbers, booleans and other scalars keep their type, since
   * processes may branch on them. Values of types other than {@link Map}/{@link Collection}/{@link
   * String} are passed through untouched — a secret held in a field of a custom object is not
   * reached.
   */
  private static Object maskSecrets(Object value, List<String> secrets) {
    return maskSecrets(value, secrets, Collections.newSetFromMap(new IdentityHashMap<>()));
  }

  private static Object maskSecrets(Object value, List<String> secrets, Set<Object> enclosing) {
    return switch (value) {
      case null -> null;
      case String string -> hideSecretsFromMessage(string, secrets);
      case Map<?, ?> map -> {
        // error variables are user-supplied, so a container may contain itself
        if (!enclosing.add(map)) {
          yield CIRCULAR_REFERENCE;
        }
        try {
          // keys are masked too, and collapsed keys simply overwrite rather than fail
          var masked = new LinkedHashMap<String, Object>();
          map.forEach(
              (key, entry) ->
                  masked.put(
                      hideSecretsFromMessage(String.valueOf(key), secrets),
                      maskSecrets(entry, secrets, enclosing)));
          yield masked;
        } finally {
          enclosing.remove(map);
        }
      }
      case Collection<?> collection -> {
        if (!enclosing.add(collection)) {
          yield CIRCULAR_REFERENCE;
        }
        try {
          yield collection.stream().map(item -> maskSecrets(item, secrets, enclosing)).toList();
        } finally {
          enclosing.remove(collection);
        }
      }
      default -> value;
    };
  }

  public ConnectorResult.ErrorResult manageConnectorJobHandlerException(
      Exception e, ActivatedJob job, Duration retryBackoffDuration, SecretFilter secretFilter) {
    List<String> secrets;
    try {
      var allowedKeys =
          SecretUtil.retrieveSecretKeysInInput(job.getVariables()).stream()
              .filter(secretFilter::isAllowed)
              .toList();
      secrets =
          this.secretProvider.fetchAll(
              allowedKeys,
              new SecretContext(
                  job.getTenantId(), job.getBpmnProcessId(), job.getPhysicalTenantId()));
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
          // secrets could not be fetched, so there is nothing to mask with
          Map.of("error", exceptionToMap(wrappedException, List.of())),
          wrappedException,
          job.getRetries() - 1);
    }
    return switch (e) {
      case InvalidBackOffDurationException invalidBackOffDurationException ->
          handleBackOffException(invalidBackOffDurationException, secrets);
      case InvalidJobTimeoutException invalidJobTimeoutException ->
          handleBackOffException(invalidJobTimeoutException, secrets);
      case ConnectorRetryException connectorRetryException ->
          handleConnectorRetryException(
              job, connectorRetryException, secrets, retryBackoffDuration);
      case Exception exception ->
          handleGenericException(job, exception, secrets, retryBackoffDuration);
    };
  }

  private static String hideSecretsFromMessage(String message, List<String> secrets) {
    if (!Objects.isNull(message))
      return secrets.stream()
          .reduce(message, (newMessage, nextSecret) -> newMessage.replace(nextSecret, "***"));
    else return "";
  }

  private ConnectorResult.ErrorResult handleBackOffException(Exception e, List<String> secrets) {
    Exception newException = new Exception(hideSecretsFromMessage(e.getMessage(), secrets), e);
    return new ConnectorResult.ErrorResult(
        Map.of("error", exceptionToMap(newException, secrets)), newException, 0);
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
        Optional.ofNullable(ex.getBackoffDuration()).orElse(retryBackoff),
        secrets);
  }

  private ConnectorResult.ErrorResult handleSDKException(
      ActivatedJob job,
      Exception ex,
      Integer retries,
      String errorCode,
      Duration backoffDuration,
      List<String> secrets) {
    LOGGER.debug(
        "Failing job with retry config => job: {} for tenant: {} with error code: {}, retries: {} and remaining backoffDuration: {}",
        job.getKey(),
        job.getTenantId(),
        errorCode,
        retries,
        backoffDuration);

    return new ConnectorResult.ErrorResult(
        Map.of("error", exceptionToMap(ex, secrets)), ex, retries, backoffDuration);
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
    return handleSDKException(job, newException, retries, errorCode, retryBackoff, secrets);
  }

  public ConnectorResult.ErrorResult handleFinalResultException(
      Exception ex, ActivatedJob job, SecretFilter secretFilter) {
    var allowedKeys =
        SecretUtil.retrieveSecretKeysInInput(job.getVariables()).stream()
            .filter(secretFilter::isAllowed)
            .toList();
    List<String> secrets =
        this.secretProvider.fetchAll(
            allowedKeys,
            new SecretContext(
                job.getTenantId(), job.getBpmnProcessId(), job.getPhysicalTenantId()));
    Exception newException = new Exception(hideSecretsFromMessage(ex.getMessage(), secrets), ex);
    LOGGER.error(
        "Exception while processing job: {} for tenant: {}, message: {}",
        job.getKey(),
        job.getTenantId(),
        ex.getMessage());
    return new ConnectorResult.ErrorResult(
        Map.of("error", exceptionToMap(newException, secrets)), newException, 0);
  }
}
