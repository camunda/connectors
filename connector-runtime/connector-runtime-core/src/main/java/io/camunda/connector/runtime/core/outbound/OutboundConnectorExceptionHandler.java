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

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.error.ConnectorRetryException;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.runtime.core.error.BpmnError;
import io.camunda.connector.runtime.core.error.ConnectorError;
import io.camunda.connector.runtime.core.error.JobError;
import io.camunda.connector.runtime.core.secret.SecretAllowListUnavailableException;
import io.camunda.connector.runtime.core.secret.SecretFailureDiagnostic;
import io.camunda.connector.runtime.core.secret.SecretFilter;
import io.camunda.connector.runtime.core.secret.SecretFilter.Secret;
import io.camunda.connector.runtime.core.secret.SecretNotAvailableException;
import io.camunda.connector.runtime.core.secret.SecretUtil;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns a failed outbound job into an {@link ConnectorResult.ErrorResult} whose message and
 * variables carry no resolved secret.
 *
 * <p>A connector is handed resolved secrets, and the API it calls can echo one back: an error
 * message or an error expression that copies a response body then publishes the value in the clear
 * to anyone who can read the incident. Recognising it can only be a value comparison — response
 * bytes carry nothing marking them as a secret — so the values the job's own input declares are
 * read back and replaced.
 */
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
   * For a job whose secret provider could not be built at all. Discovery runs before the input is
   * bound, so such a job resolved nothing: its failure cannot carry a secret value, and there is no
   * provider left to ask for one either. Withholding the message here would hide a runtime failure
   * an operator has to see, to redact values that were never substituted.
   */
  static OutboundConnectorExceptionHandler withoutSecretProvider() {
    return new OutboundConnectorExceptionHandler(null);
  }

  static Map<String, Object> exceptionToMap(Exception wrappedException, List<String> secrets) {
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
   * the original exception, and for HTTP connectors they carry the full response body and headers.
   * An API that echoes a rejected credential back would otherwise put the resolved secret into
   * process variables, which are visible to anyone who can see the process instance.
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
   * Reads the values to redact. A provider may refuse a key outright, and it throws here for keys
   * this fetch only ever wanted for masking, so the failure is returned rather than raised.
   *
   * <p>A re-read that comes back short is treated as a failure too, because redacting with a
   * partial list is what this whole path exists to prevent: the values that did come back would be
   * redacted and the one that did not would be published in the clear. That requirement is exactly
   * as strong as {@code SecretHandler}'s replacer, which throws when a provider returns null for a
   * name the filter allows — so the input could not have been bound unless every name it declares
   * resolved. One of them missing now means the secret was removed, or access to it revoked, since
   * the connector ran.
   *
   * <p>Unless the job's own failure is that very thing ({@link SecretNotAvailableException}), which
   * is the one case where a name the input declares is expected back empty. Substitution is then
   * what threw, so the input the message describes never carried that secret's value and there is
   * nothing of it to redact — and the message is the replacer's own, naming the secret an operator
   * has to go and create. Withholding it would replace the answer with a description of the
   * question.
   */
  private MaskingSecrets fetchSecretsForMasking(
      ActivatedJob job, SecretFilter secretFilter, Exception jobFailure) {
    if (secretProvider == null) {
      // see withoutSecretProvider(): nothing was resolved, so there is nothing to redact
      return new MaskingSecrets(List.of(), null);
    }
    try {
      var keys =
          allowedKeys(
              SecretUtil.retrieveSecretKeysInInput(job.getVariablesAsType(ObjectNode.class)),
              secretFilter);
      // Secret now carries fieldPath, so the same name occurring at several paths is several
      // distinct Secret values -- dedup by name here, rather than relying on the Secret list
      // itself being duplicate-free, so one provider lookup covers every occurrence of a name
      // instead of one lookup per occurrence.
      var names = keys.stream().map(Secret::secretName).distinct().toList();
      // A job that declares no secret has nothing to redact, so no provider is asked for anything:
      // a custom provider that refuses every lookup must not withhold such a job's error message.
      if (names.isEmpty()) {
        return new MaskingSecrets(List.of(), null);
      }
      var values = new ArrayList<String>(names.size());
      for (var name : names) {
        var value = secretProvider.getSecret(name);
        if (value != null) {
          values.add(value);
        }
      }
      if (values.size() < names.size() && !reportsAnUnavailableSecret(jobFailure)) {
        return new MaskingSecrets(
            List.of(),
            new MaskingSecretsIncompleteException(names.size() - values.size(), names.size()));
      }
      return new MaskingSecrets(List.copyOf(values), null);
    } catch (Exception ex) {
      LOGGER.error(
          "Initial error for job: {} for tenant: {} can't be displayed because fetching secrets failed: {}",
          job.getKey(),
          job.getTenantId(),
          safeDiagnostic(ex));
      return new MaskingSecrets(List.of(), ex);
    }
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

  private static List<Secret> allowedKeys(List<Secret> keys, SecretFilter secretFilter) {
    return keys.stream().filter(secretFilter::isAllowed).toList();
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
  private static class MaskingSecretsIncompleteException extends RuntimeException
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

  // A provider's own message can carry secret material - a bundle that parses as a non-object puts
  // the value into Jackson's coercion error - so only self-authored diagnostics are logged.
  private static String safeDiagnostic(Exception fetchFailure) {
    return fetchFailure instanceof SecretFailureDiagnostic diagnosable
        ? fetchFailure.getClass().getName() + ": " + diagnosable.publishableMessage()
        : fetchFailure.getClass().getName();
  }

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
   * <p>The failure that prevented masking is dropped with the original message, and for the same
   * reason: a provider or client error can echo a response body from the secret store, so its
   * message is no safer to publish than the message it was supposed to help redact. Only the
   * exception's class name is reported, plus, where the runtime wrote the failure itself, its
   * {@link SecretFailureDiagnostic#publishableMessage()}.
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

  /**
   * An allow-list that could not be read means no secret value ever reached the input, and the
   * lookup behind it reads the process definition from secondary storage, which a just-deployed
   * definition has yet to reach. That race resolves on the next attempt, so the read gets its own
   * backoff rather than the model's: the model's paces calls to the target system, which this
   * failure never reached, and it is zero in older element template versions -- which spent every
   * remaining attempt within milliseconds.
   */
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
      // nothing was resolved, so there is nothing to redact and no reason to ask a provider
      return handleGenericException(job, e, List.of(), retryBackoffFor(e, retryBackoffDuration));
    }
    var masking = fetchSecretsForMasking(job, secretFilter, e);
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
          Map.of("error", exceptionToMap(wrappedException, List.of())),
          wrappedException,
          retries,
          retryBackoffFor(masking.failure(), retryBackoffDuration));
    }
    List<String> secrets = withCaptured(masking, capturedSecrets);
    if (e instanceof ConnectorRetryException connectorRetryException) {
      return handleConnectorRetryException(
          job, connectorRetryException, secrets, retryBackoffDuration);
    }
    return handleGenericException(job, e, secrets, retryBackoffDuration);
  }

  /** Redacts an error an error expression produced; {@code failJob}/{@code throwError} do not. */
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

  private ConnectorResult.ErrorResult handleConnectorRetryException(
      ActivatedJob job, ConnectorRetryException ex, List<String> secrets, Duration retryBackoff) {
    Exception newException =
        new Exception(SecretUtil.hideSecretsFromMessage(ex.getMessage(), secrets), ex);
    LOGGER.debug(
        "ConnectorRetryException while processing job: {} for tenant: {}, error message: {}",
        job.getKey(),
        job.getTenantId(),
        newException.getMessage());
    return handleSDKException(
        job,
        newException,
        Optional.ofNullable(ex.getRetries()).orElse(job.getRetries() - 1),
        ex.getErrorCode(),
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
    if (ex instanceof ConnectorException connectorException) {
      errorCode = connectorException.getErrorCode();
    }
    return handleSDKException(
        job, newException, job.getRetries() - 1, errorCode, retryBackoff, secrets);
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
   * <p>The failure is logged after redaction, not before. A connector was handed resolved secrets
   * and its error message can carry one back, so logging it on the way in would put in the runtime
   * log exactly what the incident and the process variables are redacted to keep out.
   */
  public ConnectorResult.ErrorResult handleFinalResultException(
      Exception ex, ActivatedJob job, SecretFilter secretFilter, List<String> capturedSecrets) {
    var masking = fetchSecretsForMasking(job, secretFilter, ex);
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
