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

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.error.ConnectorRetryException;
import io.camunda.connector.api.secret.SecretContext;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.runtime.core.error.BpmnError;
import io.camunda.connector.runtime.core.error.ConnectorError;
import io.camunda.connector.runtime.core.error.InvalidBackOffDurationException;
import io.camunda.connector.runtime.core.error.JobError;
import io.camunda.connector.runtime.core.outbound.ConnectorResult;
import io.camunda.connector.runtime.core.secret.SecretFilter;
import io.camunda.connector.runtime.core.secret.SecretFilter.Secret;
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
        // the message is masked by the callers, but the variables are copied straight off the
        // original exception, and for HTTP connectors they carry the full response body and
        // headers: an API echoing a rejected credential back would otherwise put the resolved
        // secret into process variables
        result.put("variables", maskVariables(variables, secrets));
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

  /**
   * The values to redact from an error message, or the failure that prevented reading them.
   *
   * <p>Every call site needs the values before it can report anything, and none may let a failure
   * to read them escape: one would replace a classified result with an unclassified throw, and
   * another is already inside a catch block whose escape route abandons the job.
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
  // unions bind-time captures into a successful re-read, so a rotated secret is still redacted
  private static List<String> withCaptured(MaskingSecrets masking, List<String> captured) {
    if (captured.isEmpty()) {
      return masking.secrets();
    }
    var union = new ArrayList<String>(masking.secrets());
    union.addAll(captured);
    return union;
  }

  private MaskingSecrets fetchSecretsForMasking(
      ActivatedJob job, SecretFilter secretFilter, Exception jobFailure) {
    try {
      var allowedKeys =
          allowedKeys(
              SecretUtil.retrieveSecretKeysInInput(job.getVariablesAsType(ObjectNode.class)),
              secretFilter);
      var allowedNames = allowedKeys.stream().map(Secret::secretName).distinct().toList();
      // A job that declares no secret has nothing to redact, so no provider is asked for anything:
      // a custom fetchAll that refuses every batch must not withhold such a job's error message.
      if (allowedNames.isEmpty()) {
        return new MaskingSecrets(List.of(), null);
      }
      var values = this.secretProvider.fetchAll(allowedNames, new SecretContext(job.getTenantId()));
      // A short read means a value the connector held is missing from the redaction list, so the
      // message can't be shown: the default fetchAll drops names it can't resolve silently.
      if (values.size() < allowedNames.size() && !reportsAnUnavailableSecret(jobFailure)) {
        return new MaskingSecrets(
            List.of(),
            new MaskingSecretsIncompleteException(
                allowedNames.size() - values.size(), allowedNames.size()));
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

  private static List<Secret> allowedKeys(List<Secret> keys, SecretFilter secretFilter) {
    return keys.stream().filter(secretFilter::isAllowed).toList();
  }

  /**
   * Whether the job failed because a secret it names has no value. A re-read that comes back short
   * then says the same thing the job's own failure already says, rather than reporting a value that
   * has gone missing since the connector ran.
   */
  private static boolean reportsAnUnavailableSecret(Exception jobFailure) {
    return jobFailure != null
        && (namesAnUnavailableSecret(jobFailure)
            || namesAnUnavailableSecret(jobFailure.getCause()));
  }

  // matched on the runtime's own wording rather than a dedicated type, which this branch has no
  // public API to introduce; the message holds a secret's name, never its value
  private static boolean namesAnUnavailableSecret(Throwable failure) {
    return failure instanceof ConnectorInputException
        && failure.getMessage() != null
        && failure.getMessage().startsWith("Secret with name '")
        && failure.getMessage().endsWith("' is not available");
  }

  /**
   * Reported when the masking re-read comes back short of the secret names the job's input
   * declares. Carries a count and nothing else: how many values are missing is enough for an
   * operator to act on, and is not something a secret store told this runtime.
   */
  private static final class MaskingSecretsIncompleteException extends RuntimeException
      implements SecretFailureDiagnostic {

    private final String publishableMessage;

    private MaskingSecretsIncompleteException(int missing, int expected) {
      super(
          missing
              + " of "
              + expected
              + " secrets named by this job's input could not be read back");
      this.publishableMessage =
          missing
              + " of the "
              + expected
              + " secrets this job's input names could not be read back, so the error message could"
              + " not be redacted. A secret that resolved when the input was bound has since been"
              + " removed, or access to it revoked.";
    }

    @Override
    public String publishableMessage() {
      return publishableMessage;
    }
  }

  /**
   * A provider's own message can carry secret material — a bundle that parses as a non-object puts
   * the value it could not coerce into Jackson's error — so only the exception's class name, which
   * carries no request or response data, is reported.
   *
   * <p>Where the failure is one the runtime raised itself, its own message is reported too: a type
   * name alone does not say how many values are missing, and text written here to be read is not
   * provider output.
   */
  private static String safeDiagnostic(Exception fetchFailure) {
    return fetchFailure instanceof SecretFailureDiagnostic diagnosable
        ? fetchFailure.getClass().getName() + ": " + diagnosable.publishableMessage()
        : fetchFailure.getClass().getName();
  }

  /**
   * Stands in for an error that cannot be shown. With no values to redact with, the original
   * message has to be dropped rather than reported unmasked — it may hold a resolved secret. The
   * failure that prevented masking is dropped with it, and for the same reason.
   */
  private static String unmaskableErrorMessage(Exception fetchFailure) {
    return "Fetching secrets failed, original error can't be displayed as the error message might"
        + " contain secrets: "
        + safeDiagnostic(fetchFailure);
  }

  /**
   * Wraps {@link #unmaskableErrorMessage} for the incident payload, carrying no cause: a provider
   * or client error can echo a response body from the secret store, so its message is no safer to
   * publish than the message it was supposed to help redact, and nothing built from it could be
   * masked either — the redaction list is empty by definition on this path.
   */
  private static RuntimeException unmaskableError(Exception fetchFailure) {
    return new SecretsUnavailableException(unmaskableErrorMessage(fetchFailure));
  }

  /**
   * Reported in place of an error whose message could not be redacted.
   *
   * <p>Deliberately not a {@link ConnectorException}: {@link #exceptionToMap} copies a {@code
   * ConnectorException}'s error variables and error code into the payload, and on this path it
   * would copy them with an empty redaction list — publishing unmasked exactly the data this branch
   * exists to withhold.
   */
  private static final class SecretsUnavailableException extends RuntimeException {

    private SecretsUnavailableException(String message) {
      super(message);
    }
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

  /**
   * Whether an exception says the input can never bind, whoever raised it. Such a job is not worth
   * another attempt; anything else — an unreachable cluster, a timeout — is.
   */
  private static boolean isFatalInputError(Throwable e) {
    return e instanceof ConnectorInputException || e.getCause() instanceof ConnectorInputException;
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
    var masking = fetchSecretsForMasking(job, secretFilter, e);
    if (masking.unavailable()) {
      var wrappedException = unmaskableError(masking.failure());
      // Either failure can be the permanent one, so both are consulted. A provider that refuses to
      // resolve at all throws for every key, including the ones this fetch only needed for masking;
      // and the job's own failure is still whatever it was, so an input error that will never bind
      // must not become retryable just because reading the values to redact it happened to time
      // out. Only when neither says the input is at fault does the job keep its attempts.
      int retries =
          isFatalInputError(masking.failure()) || isFatalInputError(e) ? 0 : job.getRetries() - 1;
      return new ConnectorResult.ErrorResult(
          Map.of("error", exceptionToMap(wrappedException, List.of())),
          wrappedException,
          retries,
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

  /** Redacts an error an error expression produced; {@code failJob}/{@code throwError} do not. */
  public ConnectorError maskConnectorError(
      ConnectorError error,
      ActivatedJob job,
      SecretFilter secretFilter,
      List<String> capturedSecrets) {
    return switch (error) {
      case BpmnError bpmnError -> {
        if (nothingToRedact(bpmnError.errorMessage(), bpmnError.variables())) {
          yield bpmnError;
        }
        var masking = fetchSecretsForMasking(job, secretFilter, null);
        // the error code is never redacted: boundary events match on it
        if (masking.unavailable()) {
          yield new BpmnError(
              bpmnError.errorCode(), unmaskableErrorMessage(masking.failure()), Map.of());
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
        var masking = fetchSecretsForMasking(job, secretFilter, null);
        if (masking.unavailable()) {
          yield new JobError(
              unmaskableErrorMessage(masking.failure()),
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
    if (isFatalInputError(ex)) {
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
      // unretryable like every other failure reported here: the connector has already run, so a
      // retry would repeat its side effects
      return new ConnectorResult.ErrorResult(
          Map.of("error", exceptionToMap(wrappedException, List.of())), wrappedException, 0);
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
