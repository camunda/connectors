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
import io.camunda.connector.runtime.core.secret.SecretFailureDiagnostic;
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

  /**
   * The values to redact from an error message, or the failure that prevented reading them.
   *
   * <p>Both call sites need the values before they can report anything, and neither may let a
   * failure to read them escape: one would replace a classified result with an unclassified throw,
   * and the other is already inside a catch block whose escape route abandons the job.
   */
  private record MaskingSecrets(List<String> secrets, Exception failure) {

    boolean unavailable() {
      return failure != null;
    }
  }

  /**
   * Reads the values to redact. A provider may refuse a key outright — legacy resolution switched
   * off, a name that cannot be migrated — and it throws here for keys this fetch only ever wanted
   * for masking, so the failure is returned rather than raised.
   */
  private MaskingSecrets fetchSecretsForMasking(ActivatedJob job, SecretFilter secretFilter) {
    try {
      var allowedKeys =
          SecretUtil.retrieveSecretKeysInInput(job.getVariables()).stream()
              .filter(secretFilter::isAllowed)
              .toList();
      return new MaskingSecrets(
          this.secretProvider.fetchAll(
              allowedKeys,
              new SecretContext(
                  job.getTenantId(), job.getBpmnProcessId(), job.getPhysicalTenantId())),
          null);
    } catch (Exception ex) {
      LOGGER.error(
          "Initial error for job: {} for tenant: {} can't be displayed because fetching secrets failed: {}",
          job.getKey(),
          job.getTenantId(),
          ex.getMessage());
      return new MaskingSecrets(List.of(), ex);
    }
  }

  /**
   * Stands in for an error that cannot be shown. With no values to redact with, the original
   * message has to be dropped rather than reported unmasked — it may hold a resolved secret.
   *
   * <p>The failure that prevented masking is dropped with it, and for the same reason: a provider
   * or client error can echo a response body from the secret store, so its message is no safer to
   * publish than the message it was supposed to help redact. Nothing built from it can be masked
   * either — the redaction list is empty by definition on this path. It is logged where it happens
   * and goes no further.
   *
   * <p>The exception's class name is reported, which carries no request or response data. And where
   * the failure is one the runtime raised itself, so is its {@link
   * SecretFailureDiagnostic#publishableMessage()}: the two failures this runtime introduces —
   * legacy resolution switched off, and a name with no reference form — are precisely the ones an
   * operator has to act on, and a type name alone does not say which setting to change or which
   * charset a name has to fit. Withholding arbitrary provider text is not a reason to withhold text
   * written to be read.
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
   * would copy them with an empty redaction list — publishing unmasked exactly the data this branch
   * exists to withhold.
   */
  private static class SecretsUnavailableException extends RuntimeException {

    private SecretsUnavailableException(String failureType, String publishableMessage) {
      super(
          "Fetching secrets failed, so the original error cannot be displayed: with nothing to"
              + " redact with it might reveal a secret. Fetching failed with: "
              + failureType
              + (publishableMessage == null ? "" : ": " + publishableMessage));
    }
  }

  /**
   * Whether an exception says the input can never bind, whoever raised it. Such a job is not worth
   * another attempt; anything else — an unreachable cluster, a timeout — is.
   */
  private static boolean isFatalInputError(Throwable e) {
    return e instanceof ConnectorInputException || e.getCause() instanceof ConnectorInputException;
  }

  public ConnectorResult.ErrorResult manageConnectorJobHandlerException(
      Exception e, ActivatedJob job, Duration retryBackoffDuration, SecretFilter secretFilter) {
    var masking = fetchSecretsForMasking(job, secretFilter);
    if (masking.unavailable()) {
      var wrappedException = unmaskableError(masking.failure());
      // Either failure can be the permanent one, so both are consulted. A provider that refuses
      // to resolve at all (e.g. legacy resolution switched off) throws for every key, including
      // the ones this fetch only needed for masking; and the job's own failure is still whatever
      // it was, so an input error that will never bind must not become retryable just because
      // reading the values to redact it happened to time out. Only when neither says the input is
      // at fault does the job keep its remaining attempts.
      int retries =
          isFatalInputError(masking.failure()) || isFatalInputError(e) ? 0 : job.getRetries() - 1;
      return new ConnectorResult.ErrorResult(
          // secrets could not be fetched, so there is nothing to mask with
          Map.of("error", exceptionToMap(wrappedException, List.of())), wrappedException, retries);
    }
    List<String> secrets = masking.secrets();
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
    if (isFatalInputError(ex)) {
      retries = 0;
    }
    return handleSDKException(job, newException, retries, errorCode, retryBackoff, secrets);
  }

  /**
   * Reports a failure raised while processing a connector's final result — its result or error
   * expression.
   *
   * <p>This must not throw. Its only caller is already handling the failure it is being told about,
   * and an exception leaving here escapes that handler entirely: the job is then neither completed
   * nor failed, and stays put until its activation timeout hands it to another worker, which
   * re-runs the connector. So a masking fetch that fails costs the original message — which cannot
   * be shown unredacted — and nothing else.
   *
   * <p>The result is unretryable either way. A result expression that does not evaluate will not
   * evaluate on the next attempt, and reaching here at all means the connector has already run, so
   * a retry would repeat its side effects.
   *
   * <p>The failure is logged after redaction, not before. A connector was handed resolved secrets
   * and its error message can carry one back, so logging it on the way in would put in the runtime
   * log exactly what the incident and the process variables are redacted to keep out. Where the
   * values to redact with cannot be read, the type is logged and the message is dropped, on the
   * same reasoning that drops it from the payload. The failure that prevented redaction is the one
   * exception, and only in the log: it is the reason an operator has nothing else to go on, and
   * withholding it there would leave the runtime silent about why.
   */
  public ConnectorResult.ErrorResult handleFinalResultException(
      Exception ex, ActivatedJob job, SecretFilter secretFilter) {
    var masking = fetchSecretsForMasking(job, secretFilter);
    if (masking.unavailable()) {
      LOGGER.error(
          "Exception while processing job: {} for tenant: {}, type: {}. Its message is withheld:"
              + " the values to redact it with could not be read.",
          job.getKey(),
          job.getTenantId(),
          ex.getClass().getName());
      var wrappedException = unmaskableError(masking.failure());
      return new ConnectorResult.ErrorResult(
          Map.of("error", exceptionToMap(wrappedException, List.of())), wrappedException, 0);
    }
    List<String> secrets = masking.secrets();
    Exception newException = new Exception(hideSecretsFromMessage(ex.getMessage(), secrets), ex);
    LOGGER.error(
        "Exception while processing job: {} for tenant: {}, message: {}",
        job.getKey(),
        job.getTenantId(),
        newException.getMessage());
    return new ConnectorResult.ErrorResult(
        Map.of("error", exceptionToMap(newException, secrets)), newException, 0);
  }
}
