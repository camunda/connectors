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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.secret.SecretContext;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.runtime.core.outbound.ConnectorResult;
import io.camunda.connector.runtime.core.secret.SecretFilter;
import io.camunda.connector.runtime.core.secret.SecretLookupRefusedException;
import io.camunda.connector.runtime.core.secret.SecretReferenceResolver;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

/**
 * Secrets are masked out of error output by re-resolving them, so this handler has to resolve
 * against the same scope {@code JobHandlerContext} used when it substituted them. If the two
 * disagree, a secret resolved for one engine would not be recognized here and would leak into the
 * error payload verbatim.
 */
class OutboundConnectorExceptionHandlerTest {

  private final SecretProvider secretProvider = mock(SecretProvider.class);
  private final OutboundConnectorExceptionHandler handler =
      new OutboundConnectorExceptionHandler(secretProvider);

  private static ActivatedJob jobOnEngine(String physicalTenantId) {
    var job = mock(ActivatedJob.class);
    when(job.getVariables()).thenReturn("{\"token\": \"{{secrets.FOO}}\"}");
    when(job.getTenantId()).thenReturn("my-tenant");
    when(job.getBpmnProcessId()).thenReturn("my-process");
    when(job.getPhysicalTenantId()).thenReturn(physicalTenantId);
    return job;
  }

  private SecretContext captureSecretContext() {
    var captor = ArgumentCaptor.forClass(SecretContext.class);
    verify(secretProvider).fetchAll(any(), captor.capture());
    return captor.getValue();
  }

  @Test
  void manageConnectorJobHandlerException_resolvesSecretsAgainstTheJobsPhysicalTenant() {
    var job = jobOnEngine("engine-1");
    when(secretProvider.fetchAll(any(), any())).thenReturn(List.of("secret-value"));

    handler.manageConnectorJobHandlerException(
        new RuntimeException("boom"), job, Duration.ofSeconds(1), SecretFilter.allowAll());

    assertThat(captureSecretContext())
        .isEqualTo(new SecretContext("my-tenant", "my-process", "engine-1"));
  }

  @Test
  void manageConnectorJobHandlerException_failsWithoutRetryWhenSecretsCannotBeFetchedAtAll() {
    // a provider that refuses every lookup (e.g. legacy resolution switched off) throws for the
    // masking fetch too; retrying will not change that, so this must not be treated as transient
    var job = jobOnEngine("engine-1");
    when(job.getRetries()).thenReturn(3);
    when(secretProvider.fetchAll(any(), any()))
        .thenThrow(new ConnectorInputException("secret 'FOO' was not resolved"));

    var result =
        handler.manageConnectorJobHandlerException(
            new RuntimeException("boom"), job, Duration.ofSeconds(1), SecretFilter.allowAll());

    assertThat(result.retries()).isZero();
  }

  @Test
  void manageConnectorJobHandlerException_retriesNormallyWhenFetchingSecretsFailsTransiently() {
    var job = jobOnEngine("engine-1");
    when(job.getRetries()).thenReturn(3);
    when(secretProvider.fetchAll(any(), any())).thenThrow(new RuntimeException("timed out"));

    var result =
        handler.manageConnectorJobHandlerException(
            new RuntimeException("boom"), job, Duration.ofSeconds(1), SecretFilter.allowAll());

    assertThat(result.retries()).isEqualTo(2);
  }

  @Test
  void manageConnectorJobHandlerException_retriesWhenTheClusterCouldNotBeReached() {
    // The central-store fallback raises this when the resolve command itself failed, as opposed to
    // the cluster answering that it does not hold the name. Nothing is known about the input, so
    // the job has to keep its remaining attempts — which is exactly what the type is chosen for.
    var job = jobOnEngine("engine-1");
    when(job.getRetries()).thenReturn(3);
    when(secretProvider.fetchAll(any(), any()))
        .thenThrow(
            new SecretReferenceResolver.SecretResolutionFailedException(1, "TimeoutException"));

    var result =
        handler.manageConnectorJobHandlerException(
            new RuntimeException("boom"), job, Duration.ofSeconds(1), SecretFilter.allowAll());

    assertThat(result.retries()).isEqualTo(2);
  }

  @Test
  void manageConnectorJobHandlerException_keepsTheOriginalErrorFatalWhenMaskingFailsTransiently() {
    // The two failures are independent: the job's own can be permanent while reading the values to
    // redact it merely times out. Classifying from the masking failure alone would hand a job that
    // can never bind its remaining attempts back.
    var job = jobOnEngine("engine-1");
    when(job.getRetries()).thenReturn(3);
    when(secretProvider.fetchAll(any(), any())).thenThrow(new RuntimeException("timed out"));

    var result =
        handler.manageConnectorJobHandlerException(
            new ConnectorInputException("secret 'FOO' is not available"),
            job,
            Duration.ofSeconds(1),
            SecretFilter.allowAll());

    assertThat(result.retries()).isZero();
  }

  @Test
  void handleFinalResultException_resolvesSecretsAgainstTheJobsPhysicalTenant() {
    var job = jobOnEngine("engine-1");
    when(job.getRetries()).thenReturn(1);
    when(secretProvider.fetchAll(any(), any())).thenReturn(List.of("secret-value"));

    handler.handleFinalResultException(new RuntimeException("boom"), job, SecretFilter.allowAll());

    assertThat(captureSecretContext())
        .isEqualTo(new SecretContext("my-tenant", "my-process", "engine-1"));
  }

  @Test
  void handleFinalResultException_reportsAnErrorWhenTheMaskingFetchFails() {
    // The only caller is already inside a catch block, and an exception leaving here escapes it:
    // the job would then be neither completed nor failed, sitting until its activation timeout
    // hands it to another worker, which re-runs a connector that has already run. So a masking
    // fetch that fails has to come back as a result, not as a throw.
    var job = jobOnEngine("engine-1");
    when(job.getRetries()).thenReturn(3);
    when(secretProvider.fetchAll(any(), any()))
        .thenThrow(new ConnectorInputException("secret 'FOO' was not resolved"));

    var result =
        handler.handleFinalResultException(
            new RuntimeException("boom"), job, SecretFilter.allowAll());

    assertThat(result).isNotNull();
    assertThat(result.retries()).isZero();
  }

  @Test
  void handleFinalResultException_withholdsTheOriginalMessageWhenItCannotBeMasked() {
    // Nothing to mask with means the original message cannot be shown: it may hold a resolved
    // secret, and these variables are visible to anyone who can see the process instance.
    var job = jobOnEngine("engine-1");
    when(job.getRetries()).thenReturn(3);
    when(secretProvider.fetchAll(any(), any())).thenThrow(new RuntimeException("timed out"));

    var result =
        handler.handleFinalResultException(
            new RuntimeException("failed talking to https://api?key=super-secret"),
            job,
            SecretFilter.allowAll());

    assertThat(result.responseValue().toString()).doesNotContain("super-secret");
  }

  @Test
  void handleFinalResultException_logsTheFailureOnlyAfterRedaction() {
    // The runtime log is a third channel, alongside the payload and the incident message. A
    // connector was handed resolved secrets, so its error message can carry one back; logging it
    // on the way in would put in the log exactly what the other two channels redact.
    var job = jobOnEngine("engine-1");
    when(job.getRetries()).thenReturn(3);
    when(secretProvider.fetchAll(any(), any())).thenReturn(List.of("super-secret"));

    var logged =
        logsOf(
            () ->
                handler.handleFinalResultException(
                    new RuntimeException("failed talking to https://api?key=super-secret"),
                    job,
                    SecretFilter.allowAll()));

    assertThat(logged).noneMatch(message -> message.contains("super-secret"));
    assertThat(logged).anyMatch(message -> message.contains("***"));
  }

  @Test
  void handleFinalResultException_logsOnlyTheTypeWhenTheMessageCannotBeRedacted() {
    var job = jobOnEngine("engine-1");
    when(job.getRetries()).thenReturn(3);
    when(secretProvider.fetchAll(any(), any())).thenThrow(new RuntimeException("timed out"));

    var logged =
        logsOf(
            () ->
                handler.handleFinalResultException(
                    new RuntimeException("failed talking to https://api?key=super-secret"),
                    job,
                    SecretFilter.allowAll()));

    assertThat(logged).noneMatch(message -> message.contains("super-secret"));
    assertThat(logged).anyMatch(message -> message.contains("java.lang.RuntimeException"));
  }

  /**
   * Every message this handler logs while {@code action} runs, formatted as it would be written.
   */
  private static List<String> logsOf(Runnable action) {
    var logger = (Logger) LoggerFactory.getLogger(OutboundConnectorExceptionHandler.class);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    try {
      action.run();
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
    return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
  }

  @Test
  void aFailedMaskingFetchIsNeverItselfPublished() {
    // The fetch failure is no safer to publish than the message it was meant to help redact: a
    // provider or client error can echo a response body from the secret store. Nothing built from
    // it can be masked either, since the redaction list is empty by definition on this path.
    var job = jobOnEngine("engine-1");
    when(job.getRetries()).thenReturn(3);
    when(secretProvider.fetchAll(any(), any()))
        .thenThrow(new RuntimeException("store replied: {\"token\":\"super-secret\"}"));

    var result =
        handler.manageConnectorJobHandlerException(
            new RuntimeException("boom"), job, Duration.ofSeconds(1), SecretFilter.allowAll());

    // Both channels: the payload becomes process variables, and the message becomes the incident
    // message that prepareFailJobCommand sends to Zeebe.
    assertThat(result.responseValue().toString()).doesNotContain("super-secret");
    assertThat(result.exception().getMessage()).doesNotContain("super-secret");
    // The class name is what an operator needs, and carries no request or response data.
    assertThat(result.exception().getMessage()).contains("java.lang.RuntimeException");
  }

  @Test
  void aRuntimeAuthoredDiagnosticSurvivesTheMaskingFailure() {
    // Under OFF the operator's fix is to change the model, and the setting plus the form that
    // replaced it is the whole diagnostic. It is authored by the runtime, not taken from a
    // provider, so withholding arbitrary provider text is no reason to withhold this.
    var job = jobOnEngine("engine-1");
    when(job.getRetries()).thenReturn(3);
    when(secretProvider.fetchAll(any(), any()))
        .thenThrow(
            new SecretLookupRefusedException(
                "Legacy secret resolution is disabled"
                    + " (camunda.connector.secret-resolver.legacy.mode=OFF); secret 'FOO' was not"
                    + " resolved. Reference secrets as camunda.secrets.<name> instead."));

    var result =
        handler.manageConnectorJobHandlerException(
            new RuntimeException("boom"), job, Duration.ofSeconds(1), SecretFilter.allowAll());

    assertThat(result.exception().getMessage())
        .contains("camunda.connector.secret-resolver.legacy.mode=OFF")
        .contains("camunda.secrets.<name>");
    // Still a permanent input error: the model has to change, so retrying cannot help.
    assertThat(result.retries()).isZero();
  }

  @Test
  void aFailedMaskingFetchDoesNotPublishItsOwnErrorVariables() {
    // exceptionToMap copies a ConnectorException's variables and code into the payload. On this
    // path it would copy them with an empty redaction list, publishing unmasked exactly the data
    // the branch exists to withhold.
    var job = jobOnEngine("engine-1");
    when(job.getRetries()).thenReturn(3);
    when(secretProvider.fetchAll(any(), any()))
        .thenThrow(
            new ConnectorException(
                "PROVIDER_CODE",
                "lookup rejected",
                null,
                Map.of("response", "credential super-secret was rejected")));

    var result =
        handler.manageConnectorJobHandlerException(
            new RuntimeException("boom"), job, Duration.ofSeconds(1), SecretFilter.allowAll());

    assertThat(result.responseValue().toString())
        .doesNotContain("super-secret")
        .doesNotContain("PROVIDER_CODE");
  }

  @Test
  void handleFinalResultException_masksASecretScopedToTheJobsEngine() {
    // the payoff of scoping correctly: this provider only knows the secret under engine-1, so
    // resolving with the wrong physical tenant leaves the value unmasked in the error payload
    var job = jobOnEngine("engine-1");
    when(job.getRetries()).thenReturn(1);
    SecretProvider engineScopedProvider =
        new SecretProvider() {
          @Override
          public String getSecret(String name, SecretContext context) {
            return "FOO".equals(name) && "engine-1".equals(context.physicalTenantId())
                ? "super-secret"
                : null;
          }
        };

    var result =
        new OutboundConnectorExceptionHandler(engineScopedProvider)
            .handleFinalResultException(
                new RuntimeException("failed talking to https://api?key=super-secret"),
                job,
                SecretFilter.allowAll());

    assertThat(result.responseValue().toString()).doesNotContain("super-secret");
  }

  /**
   * The error message was already masked, but the error variables are copied straight off the
   * original exception. For HTTP connectors those carry the whole response body and headers, so an
   * API that echoes a rejected credential back used to publish the resolved secret as a process
   * variable.
   */
  @Test
  void errorVariables_maskSecretsNestedInAnHttpResponseBody() {
    var errorVariables =
        Map.<String, Object>of(
            "response",
            Map.of(
                "headers",
                Map.of("www-authenticate", "Bearer error=\"invalid_token super-secret\""),
                "body",
                Map.of("errors", List.of(Map.of("detail", "token super-secret is not valid")))));

    var masked = maskedErrorVariables(errorVariables);

    assertThat(masked.toString()).doesNotContain("super-secret").contains("***");
  }

  @Test
  void errorVariables_maskSecretsUsedAsMapKeys() {
    var masked = maskedErrorVariables(Map.of("super-secret", "harmless"));

    assertThat(masked).containsOnlyKeys("***");
  }

  /** Processes branch on these, so rewriting them into strings would change behaviour. */
  @Test
  void errorVariables_leaveNonStringLeavesUntouched() {
    var errorVariables = new HashMap<String, Object>();
    errorVariables.put("status", 401);
    errorVariables.put("retryable", false);
    errorVariables.put("body", null);

    var masked = maskedErrorVariables(errorVariables);

    assertThat(masked).containsEntry("status", 401).containsEntry("retryable", false);
    assertThat(masked).containsKey("body");
    assertThat(masked.get("body")).isNull();
  }

  /** Error variables are supplied by connector authors, so a container may contain itself. */
  @Test
  void errorVariables_terminateOnSelfReferencingContainers() {
    var errorVariables = new HashMap<String, Object>();
    errorVariables.put("detail", "token super-secret rejected");
    errorVariables.put("self", errorVariables);

    var masked = maskedErrorVariables(errorVariables);

    assertThat(masked).containsEntry("detail", "token *** rejected");
    assertThat(masked).containsEntry("self", "[circular reference]");
  }

  /**
   * BPMN error boundary events match on the error code, so masking it would silently stop the error
   * from being caught. Codes come from HTTP statuses and connector-authored constants, not from
   * remote payloads.
   */
  @Test
  void errorCode_isNotMasked() {
    var job = jobOnEngine("engine-1");
    when(job.getRetries()).thenReturn(1);
    when(secretProvider.fetchAll(any(), any())).thenReturn(List.of("401"));

    var result =
        handler.handleFinalResultException(
            new ConnectorException("401", "Unauthorized"), job, SecretFilter.allowAll());

    assertThat(error(result)).containsEntry("code", "401");
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> maskedErrorVariables(Map<String, Object> errorVariables) {
    var job = jobOnEngine("engine-1");
    when(job.getRetries()).thenReturn(1);
    when(secretProvider.fetchAll(any(), any())).thenReturn(List.of("super-secret"));

    var result =
        handler.handleFinalResultException(
            new ConnectorException("401", "Unauthorized", null, errorVariables),
            job,
            SecretFilter.allowAll());

    return (Map<String, Object>) error(result).get("variables");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> error(ConnectorResult.ErrorResult result) {
    return (Map<String, Object>) ((Map<String, Object>) result.responseValue()).get("error");
  }
}
