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

import static io.camunda.connector.runtime.core.outbound.ConnectorJobHandler.MAX_ERROR_MESSAGE_LENGTH;

import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.error.ConnectorRetryException;
import io.camunda.connector.api.secret.SecretContext;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.runtime.core.error.BpmnError;
import io.camunda.connector.runtime.core.error.ConnectorError;
import io.camunda.connector.runtime.core.error.InvalidBackOffDurationException;
import io.camunda.connector.runtime.core.error.JobError;
import io.camunda.connector.runtime.core.secret.SecretAllowListUnavailableException;
import io.camunda.connector.runtime.core.secret.SecretFailureDiagnostic;
import io.camunda.connector.runtime.core.secret.SecretFilter;
import io.camunda.connector.runtime.core.secret.SecretNotAvailableException;
import io.camunda.connector.runtime.core.secret.SecretUtil;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import java.time.Duration;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OutboundConnectorExceptionHandler {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(OutboundConnectorExceptionHandler.class);

  private static final Duration ALLOW_LIST_RETRY_BACKOFF = Duration.ofSeconds(5);

  private static final String CIRCULAR_REFERENCE = "[circular reference]";

  private final SecretProvider secretProvider;

  public OutboundConnectorExceptionHandler(SecretProvider secretProvider) {
    this.secretProvider = secretProvider;
  }

  /**
   * Stands in as the returned exception's cause in the legacy no-filter overloads below, so that a
   * caller walking the cause chain never reaches the real exception's message or, if it is a {@link
   * ConnectorException}, its error variables -- either can carry a resolved secret, and neither
   * overload has a filter to redact it with. See {@link #withheldExceptionToMap} for how the
   * returned map itself stays free of both, keeping only {@code type}.
   */
  private static final class SecretFilterUnavailableException extends RuntimeException {}

  private static Map<String, Object> exceptionToMap(
      Exception wrappedException, List<String> secrets) {
    Map<String, Object> result = new HashMap<>();
    // Every wrapper built here carries the failure it reports as its cause, except the one that
    // deliberately withholds it — that one reports itself, rather than dereferencing a null.
    Throwable originalCause =
        wrappedException.getCause() != null ? wrappedException.getCause() : wrappedException;
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
        // the message is masked by the callers, but these are copied straight off the original
        // exception: for HTTP connectors they carry the whole response body and headers, so an API
        // that echoes a rejected credential back would publish the resolved secret as a variable
        result.put("variables", maskSecrets(variables, secrets));
      }
    }
    return Map.copyOf(result);
  }

  /**
   * Builds the error map for the legacy no-filter overloads: {@code type} is read straight off
   * {@code e}'s class, which can never carry a secret. {@code code} is deliberately omitted even
   * for a {@link ConnectorException} -- unlike {@code type}, it's an arbitrary caller-supplied
   * string ({@link io.camunda.connector.api.error.ConnectorExceptionBuilder#errorCode}), so nothing
   * rules out a connector constructing one from a resolved secret. {@code message} always comes
   * from {@code wrappedException}'s fixed, filter-free text, and {@code variables} is never
   * included.
   */
  private static Map<String, Object> withheldExceptionToMap(
      Exception e, Exception wrappedException) {
    Map<String, Object> result = new HashMap<>();
    result.put("type", e.getClass().getName());
    var message = wrappedException.getMessage();
    if (message != null) {
      result.put(
          "message", message.substring(0, Math.min(message.length(), MAX_ERROR_MESSAGE_LENGTH)));
    }
    return Map.copyOf(result);
  }

  /**
   * Preserves the pre-existing three-argument overload for callers compiled against it. This
   * overload has no filter to redact secrets with, so it withholds the original message outright
   * rather than falling back to an unfiltered {@link SecretFilter#allowAll()} — the whole point of
   * the secret filter is to restrict what gets resolved, and defaulting to allow-all here would let
   * a legacy caller bypass that restriction. It still classifies {@code e} by type to preserve the
   * pre-existing retry/backoff semantics per exception type, but never attaches {@code e} itself
   * (or its message) to the returned result.
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
            new SecretFilterUnavailableException());
    var errorPayload = Map.of("error", withheldExceptionToMap(e, wrappedException));
    return switch (e) {
      case InvalidBackOffDurationException ignored ->
          new ConnectorResult.ErrorResult(errorPayload, wrappedException, 0);
      case ConnectorRetryException connectorRetryException ->
          new ConnectorResult.ErrorResult(
              errorPayload,
              wrappedException,
              Optional.ofNullable(connectorRetryException.getRetries())
                  .orElse(job.getRetries() - 1),
              Optional.ofNullable(connectorRetryException.getBackoffDuration())
                  .orElse(retryBackoffDuration));
      case Exception exception -> {
        int retries = job.getRetries() - 1;
        if (exception instanceof ConnectorInputException
            || exception.getCause() instanceof ConnectorInputException) {
          retries = 0;
        }
        yield new ConnectorResult.ErrorResult(errorPayload, wrappedException, retries);
      }
    };
  }

  private static Duration retryBackoffFor(Exception failure, Duration configured) {
    return failure instanceof SecretAllowListUnavailableException
        ? ALLOW_LIST_RETRY_BACKOFF
        : configured;
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

  /**
   * The values to redact from an error message, or the failure that prevented reading them. An
   * empty list with no failure means the job declares nothing to redact, which is not the same as
   * being unable to tell.
   */
  private record MaskingSecrets(List<String> secrets, Exception failure) {
    boolean unavailable() {
      return failure != null;
    }
  }

  /**
   * Reads the values to redact an error with. A job that declares no secret is never handed to the
   * provider at all: {@code fetchAll} is overridable, and one that refuses every batch would
   * otherwise withhold the error message of a job that had nothing to redact in the first place.
   *
   * @param jobFailure the failure being reported, or {@code null} where the caller is redacting
   *     something other than a failure
   */
  private MaskingSecrets fetchSecretsForMasking(
      ActivatedJob job, SecretFilter secretFilter, Exception jobFailure) {
    try {
      var allowedKeys =
          SecretUtil.retrieveSecretKeysInInput(job.getVariables()).stream()
              .filter(secretFilter::isAllowed)
              .toList();
      if (allowedKeys.isEmpty()) {
        return new MaskingSecrets(List.of(), null);
      }
      var values = this.secretProvider.fetchAll(allowedKeys, new SecretContext(job.getTenantId()));
      // fetchAll drops the names it cannot resolve, so a short read is a silent one: a name the
      // input declares resolved when it was bound, so one missing now means the secret was removed,
      // or access revoked, while the connector ran. Redacting with what did come back would publish
      // the one that did not in the clear.
      if (values.size() < allowedKeys.size() && !reportsAnUnavailableSecret(jobFailure)) {
        return new MaskingSecrets(
            List.of(),
            new MaskingSecretsIncompleteException(
                allowedKeys.size() - values.size(), allowedKeys.size()));
      }
      return new MaskingSecrets(values, null);
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
   * Whether the job failed because a secret it names has no value. A re-read that comes back short
   * then says the same thing the job's own failure already says, rather than reporting a value that
   * has gone missing since the connector ran.
   */
  private static boolean reportsAnUnavailableSecret(Exception jobFailure) {
    return jobFailure instanceof SecretNotAvailableException
        || (jobFailure != null && jobFailure.getCause() instanceof SecretNotAvailableException);
  }

  /**
   * Reported when the masking re-read comes back short of the names the job's input declares.
   * Carries a count and nothing else: how many values are missing is enough for an operator to act
   * on, and is not something a secret store told this runtime.
   */
  private static final class MaskingSecretsIncompleteException extends RuntimeException
      implements SecretFailureDiagnostic {

    private MaskingSecretsIncompleteException(int missing, int expected) {
      super(
          missing
              + " of the "
              + expected
              + " secrets this job's input names could not be read back. One that resolved when the"
              + " input was bound has since been removed, or access to it revoked.");
    }

    @Override
    public String publishableMessage() {
      return getMessage();
    }
  }

  /**
   * A provider's own message can carry secret material — {@code AbstractSecretProvider} folds a
   * Jackson failure into its message, and a secrets bundle that parses as a non-object puts the
   * value it could not coerce into the coercion error — so only the failure's type is reported. The
   * exception is a {@link SecretFailureDiagnostic}, whose message this runtime wrote itself and
   * which names what an operator has to act on.
   */
  private static String safeDiagnostic(Exception fetchFailure) {
    return fetchFailure instanceof SecretFailureDiagnostic diagnosable
        ? fetchFailure.getClass().getName() + ": " + diagnosable.publishableMessage()
        : fetchFailure.getClass().getName();
  }

  // unions bind-time captures into a successful re-read, so a rotated secret is still redacted
  private static List<String> withCaptured(MaskingSecrets masking, List<String> captured) {
    if (captured == null || captured.isEmpty()) {
      return masking.secrets();
    }
    var union = new ArrayList<String>(masking.secrets());
    union.addAll(captured);
    return union;
  }

  /**
   * Stands in for an error that cannot be shown. With no values to redact with, the original
   * message has to be dropped rather than reported unmasked — it may hold a resolved secret.
   *
   * <p>The failure that prevented masking is dropped with it, and for the same reason: a provider
   * or client error can echo a response body from the secret store, so its message is no safer to
   * publish than the message it was supposed to help redact. It is not attached as a cause either,
   * since anything walking the chain would reach that message again.
   */
  private static RuntimeException unmaskableError(Exception fetchFailure) {
    return new SecretsUnavailableException(
        fetchFailure.getClass().getName(),
        fetchFailure instanceof SecretFailureDiagnostic diagnosable
            ? diagnosable.publishableMessage()
            : null);
  }

  /**
   * Reported in place of an error whose message could not be redacted.
   *
   * <p>Deliberately not a {@link ConnectorException}: {@link #exceptionToMap} copies a {@code
   * ConnectorException}'s error variables and error code into the payload, and on this path it
   * would copy them with no redaction list at all — publishing unmasked exactly the data this
   * branch exists to withhold.
   */
  private static final class SecretsUnavailableException extends RuntimeException {

    private SecretsUnavailableException(String failureType, String publishableMessage) {
      super(
          "Fetching secrets failed, so the original error cannot be displayed: with nothing to"
              + " redact with it might reveal a secret. Fetching failed with: "
              + failureType
              + (publishableMessage == null ? "" : ": " + publishableMessage));
    }
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
    var masking = fetchSecretsForMasking(job, secretFilter, e);
    if (masking.unavailable()) {
      var wrappedException = unmaskableError(masking.failure());
      return new ConnectorResult.ErrorResult(
          // secrets could not be fetched, so there is nothing to mask with
          Map.of("error", exceptionToMap(wrappedException, List.of())),
          wrappedException,
          job.getRetries() - 1,
          retryBackoffFor(masking.failure(), retryBackoffDuration));
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

  /**
   * Redacts an error an error expression produced; {@code failJob}/{@code throwError} do not.
   *
   * <p>Redacting here rather than in the command builders keeps the redaction ahead of the 6000
   * character truncation, where a secret straddling the cut would leave a prefix {@link
   * String#replace} no longer sees.
   */
  public ConnectorError maskConnectorError(
      ConnectorError error,
      ActivatedJob job,
      SecretFilter secretFilter,
      List<String> capturedSecrets) {
    return switch (error) {
      case BpmnError bpmnError -> {
        if (nothingToRedact(bpmnError.message(), bpmnError.variables())) {
          yield bpmnError;
        }
        var masking = fetchSecretsForMasking(job, secretFilter, null);
        // the error code is never redacted: boundary events match on it
        if (masking.unavailable()) {
          yield new BpmnError(
              bpmnError.code(), unmaskableError(masking.failure()).getMessage(), Map.of());
        }
        var secrets = withCaptured(masking, capturedSecrets);
        yield new BpmnError(
            bpmnError.code(),
            redactNullable(bpmnError.message(), secrets),
            maskVariables(bpmnError.variables(), secrets));
      }
      case JobError jobError -> {
        if (nothingToRedact(jobError.message(), jobError.variables())) {
          yield jobError;
        }
        var masking = fetchSecretsForMasking(job, secretFilter, null);
        // the retries and the backoff are the expression author's decision, so they survive
        if (masking.unavailable()) {
          yield new JobError(
              unmaskableError(masking.failure()).getMessage(),
              Map.of(),
              jobError.retries(),
              jobError.retryBackoff());
        }
        var secrets = withCaptured(masking, capturedSecrets);
        yield new JobError(
            redactNullable(jobError.message(), secrets),
            maskVariables(jobError.variables(), secrets),
            jobError.retries(),
            jobError.retryBackoff());
      }
    };
  }

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
        Map.of("error", exceptionToMap(newException, secrets)), newException, 0);
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
    return handleSDKException(job, newException, retries, errorCode, retryBackoff, secrets);
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
            new SecretFilterUnavailableException());
    return new ConnectorResult.ErrorResult(
        Map.of("error", withheldExceptionToMap(ex, wrappedException)), wrappedException, 0);
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
      Exception ex, ActivatedJob job, SecretFilter secretFilter, List<String> capturedSecrets) {
    var masking = fetchSecretsForMasking(job, secretFilter, ex);
    if (masking.unavailable()) {
      var wrappedException = unmaskableError(masking.failure());
      return new ConnectorResult.ErrorResult(
          // secrets could not be fetched, so there is nothing to mask with
          Map.of("error", exceptionToMap(wrappedException, List.of())),
          wrappedException,
          job.getRetries() - 1);
    }
    List<String> secrets = withCaptured(masking, capturedSecrets);
    Exception newException =
        new Exception(SecretUtil.hideSecretsFromMessage(ex.getMessage(), secrets), ex);
    LOGGER.error(
        "Exception while processing job: {} for tenant: {}, message: {}",
        job.getKey(),
        job.getTenantId(),
        newException.getMessage());
    return new ConnectorResult.ErrorResult(
        Map.of("error", exceptionToMap(newException, secrets)), newException, 0);
  }
}
