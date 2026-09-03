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
import io.camunda.connector.runtime.core.error.BpmnError;
import io.camunda.connector.runtime.core.error.ConnectorError;
import io.camunda.connector.runtime.core.error.IgnoreError;
import io.camunda.connector.runtime.core.error.InvalidBackOffDurationException;
import io.camunda.connector.runtime.core.error.JobError;
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

  private static final Duration ALLOW_LIST_RETRY_BACKOFF = Duration.ofSeconds(5);

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
   * Masks every secret occurrence in {@code value}, recursing through maps and collections.
   *
   * <p>Only strings are rewritten; numbers, booleans and other scalars keep their type, since
   * processes may branch on them. Values of types other than {@link Map}/{@link Collection}/{@link
   * String} are passed through untouched — a secret held in a field of a custom object is not
   * reached.
   */
  private static Object maskSecrets(Object value, List<String> secrets, Set<Object> enclosing) {
    return switch (value) {
      case null -> null;
      case String string -> SecretUtil.hideSecretsFromMessage(string, secrets);
      case Map<?, ?> map -> {
        // error variables are user-supplied, so a container may contain itself
        if (!enclosing.add(map)) {
          yield CIRCULAR_REFERENCE;
        }
        try {
          yield maskMap(map, secrets, enclosing);
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

  private static Map<String, Object> maskMap(
      Map<?, ?> map, List<String> secrets, Set<Object> enclosing) {
    // keys are masked too, and collapsed keys simply overwrite rather than fail
    var masked = new LinkedHashMap<String, Object>();
    map.forEach(
        (key, entry) ->
            masked.put(
                SecretUtil.hideSecretsFromMessage(String.valueOf(key), secrets),
                maskSecrets(entry, secrets, enclosing)));
    return masked;
  }

  /** Typed variant of {@link #maskSecrets}, which returns {@link Object} to allow a sentinel. */
  private static Map<String, Object> maskVariables(
      Map<String, Object> variables, List<String> secrets) {
    if (variables == null) {
      return null;
    }
    Set<Object> enclosing = Collections.newSetFromMap(new IdentityHashMap<>());
    enclosing.add(variables);
    return maskMap(variables, secrets, enclosing);
  }

  // unions bind-time captures into a successful re-read, so a rotated secret is still redacted
  private static List<String> withCaptured(MaskingSecrets masking, List<String> captured) {
    if (captured.isEmpty()) {
      return masking.secrets();
    }
    var union = new ArrayList<String>(masking.secrets());
    union.addAll(captured);
    return union;
  }

  /**
   * The values to redact from an error message, or the failure that prevented reading them.
   *
   * <p>Every call site needs the values before it can report anything, and none of them may let a
   * failure to read them escape: one would replace a classified result with an unclassified throw,
   * and another is already inside a catch block whose escape route abandons the job.
   */
  private record MaskingSecrets(List<String> secrets, Exception failure) {

    boolean unavailable() {
      return failure != null;
    }
  }

  /**
   * Reads the values to redact. A provider may refuse a key outright, and it throws here for keys
   * this fetch only ever wanted for masking, so the failure is returned rather than raised.
   */
  private MaskingSecrets fetchSecretsForMasking(ActivatedJob job, SecretFilter secretFilter) {
    try {
      var allowedKeys =
          SecretUtil.retrieveSecretKeysInInput(job.getVariables()).stream()
              .filter(secretFilter::isAllowed)
              .toList();
      // A job that declares no secret has nothing to redact, so no provider is asked for anything:
      // a custom fetchAll that refuses every batch must not withhold such a job's error message.
      if (allowedKeys.isEmpty()) {
        return new MaskingSecrets(List.of(), null);
      }
      return new MaskingSecrets(
          this.secretProvider.fetchAll(
              allowedKeys, new SecretContext(job.getTenantId(), job.getBpmnProcessId())),
          null);
    } catch (Exception ex) {
      LOGGER.error(
          "Initial error for job: {} for tenant: {} can't be displayed because fetching secrets failed: {}",
          job.getKey(),
          job.getTenantId(),
          safeDiagnostic(ex));
      return new MaskingSecrets(List.of(), ex);
    }
  }

  /**
   * What may be said about a failed secret read. A provider's own message can carry secret material
   * — a bundle that parses as a non-object puts the value it could not coerce into Jackson's
   * coercion error — so only the exception's class name is reported, plus the text of the one
   * refusal this runtime writes itself: an unreadable allow-list names the element an operator has
   * to look at, and a type name alone does not.
   */
  private static String safeDiagnostic(Exception fetchFailure) {
    return fetchFailure instanceof SecretAllowListUnavailableException
        ? fetchFailure.getClass().getName() + ": " + fetchFailure.getMessage()
        : fetchFailure.getClass().getName();
  }

  /**
   * Stands in for an error that cannot be shown. With no values to redact with, the original
   * message has to be dropped rather than reported unmasked — it may hold a resolved secret.
   *
   * <p>The failure that prevented masking is dropped with it, and for the same reason: a provider
   * or client error can echo a response body from the secret store, so its message is no safer to
   * publish than the message it was supposed to help redact.
   */
  private static RuntimeException unmaskableError(Exception fetchFailure) {
    return new SecretsUnavailableException(safeDiagnostic(fetchFailure));
  }

  /**
   * Reported in place of an error whose message could not be redacted.
   *
   * <p>Deliberately not a {@link ConnectorException}: {@link #exceptionToMap} copies a {@code
   * ConnectorException}'s error variables and error code into the payload, and on this path it
   * would copy them with no redaction list at all — publishing unmasked exactly the data this
   * branch exists to withhold.
   */
  private static class SecretsUnavailableException extends RuntimeException {

    private SecretsUnavailableException(String failureType) {
      super(
          "Fetching secrets failed, so the original error cannot be displayed: with nothing to"
              + " redact with it might reveal a secret. Fetching failed with: "
              + failureType);
    }
  }

  private static Duration retryBackoffFor(Exception failure, Duration configured) {
    return failure instanceof SecretAllowListUnavailableException
        ? ALLOW_LIST_RETRY_BACKOFF
        : configured;
  }

  public ConnectorResult.ErrorResult manageConnectorJobHandlerException(
      Exception e,
      ActivatedJob job,
      Duration retryBackoffDuration,
      SecretFilter secretFilter,
      List<String> capturedSecrets) {
    if (e instanceof SecretAllowListUnavailableException) {
      return handleGenericException(job, e, List.of(), retryBackoffFor(e, retryBackoffDuration));
    }
    var masking = fetchSecretsForMasking(job, secretFilter);
    if (masking.unavailable()) {
      var ex = masking.failure();
      var wrappedException =
          new RuntimeException(
              "Fetching secrets failed, original error can't be displayed as the error message might contain secrets: "
                  + ex.getMessage(),
              ex);
      return new ConnectorResult.ErrorResult(
          Map.of("error", exceptionToMap(wrappedException)),
          wrappedException,
          job.getRetries() - 1,
          retryBackoffFor(ex, retryBackoffDuration));
    }
    List<String> secrets = withCaptured(masking, capturedSecrets);
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

  /** Redacts an error an error expression produced; {@code failJob}/{@code throwError} do not. */
  public ConnectorError maskConnectorError(
      ConnectorError error,
      ActivatedJob job,
      SecretFilter secretFilter,
      List<String> capturedSecrets) {
    return switch (error) {
      // business output: this branch completes the job with the variables the process branches on
      case IgnoreError ignoreError -> ignoreError;
      case BpmnError bpmnError -> {
        if (nothingToRedact(bpmnError.errorMessage(), bpmnError.variables())) {
          yield bpmnError;
        }
        var masking = fetchSecretsForMasking(job, secretFilter);
        // the error code is never redacted: boundary events match on it
        if (masking.unavailable()) {
          yield new BpmnError(
              bpmnError.errorCode(), unmaskableError(masking.failure()).getMessage(), Map.of());
        }
        var secrets = withCaptured(masking, capturedSecrets);
        yield new BpmnError(
            bpmnError.errorCode(),
            redactNullable(bpmnError.errorMessage(), secrets),
            maskVariables(bpmnError.variables(), secrets));
      }
      case JobError jobError -> {
        if (nothingToRedact(jobError.errorMessage(), jobError.variables())) {
          yield jobError;
        }
        var masking = fetchSecretsForMasking(job, secretFilter);
        if (masking.unavailable()) {
          yield new JobError(
              unmaskableError(masking.failure()).getMessage(),
              Map.of(),
              jobError.retries(),
              jobError.retryBackoff());
        }
        var secrets = withCaptured(masking, capturedSecrets);
        yield new JobError(
            redactNullable(jobError.errorMessage(), secrets),
            maskVariables(jobError.variables(), secrets),
            jobError.retries(),
            jobError.retryBackoff());
      }
    };
  }

  // A JobError egresses variablesWithErrorMessage(), which adds only the message checked here.
  private static boolean nothingToRedact(String errorMessage, Map<String, Object> variables) {
    return (errorMessage == null || errorMessage.isEmpty())
        && (variables == null || variables.isEmpty());
  }

  /** Unlike {@link SecretUtil#hideSecretsFromMessage}, keeps a null message null rather than "". */
  private static String redactNullable(String message, List<String> secrets) {
    return message == null ? null : SecretUtil.hideSecretsFromMessage(message, secrets);
  }

  private ConnectorResult.ErrorResult handleBackOffException(Exception e, List<String> secrets) {
    Exception newException =
        new Exception(SecretUtil.hideSecretsFromMessage(e.getMessage(), secrets), e);
    return new ConnectorResult.ErrorResult(
        Map.of("error", exceptionToMap(newException)), newException, 0);
  }

  private ConnectorResult.ErrorResult handleConnectorRetryException(
      ActivatedJob job, ConnectorRetryException ex, List<String> secrets, Duration retryBackoff) {
    Exception newException =
        new Exception(SecretUtil.hideSecretsFromMessage(ex.getMessage(), secrets), ex);
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
    Exception newException =
        new Exception(SecretUtil.hideSecretsFromMessage(ex.getMessage(), secrets), ex);
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

  public ConnectorResult.ErrorResult handleFinalResultException(
      Exception ex, ActivatedJob job, SecretFilter secretFilter, List<String> capturedSecrets) {
    var allowedKeys =
        SecretUtil.retrieveSecretKeysInInput(job.getVariables()).stream()
            .filter(secretFilter::isAllowed)
            .toList();
    List<String> secrets =
        new ArrayList<>(
            this.secretProvider.fetchAll(
                allowedKeys, new SecretContext(job.getTenantId(), job.getBpmnProcessId())));
    secrets.addAll(capturedSecrets);
    Exception newException =
        new Exception(SecretUtil.hideSecretsFromMessage(ex.getMessage(), secrets), ex);
    LOGGER.error(
        "Exception while processing job: {} for tenant: {}, message: {}",
        job.getKey(),
        job.getTenantId(),
        newException.getMessage());
    return new ConnectorResult.ErrorResult(
        Map.of("error", exceptionToMap(newException)), newException, 0);
  }
}
