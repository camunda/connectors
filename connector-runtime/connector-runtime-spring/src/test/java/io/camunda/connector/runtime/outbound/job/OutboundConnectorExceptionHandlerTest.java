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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.runtime.core.outbound.ConnectorResult;
import io.camunda.connector.runtime.core.secret.SecretFilter;
import io.camunda.connector.runtime.core.secret.SecretFilter.Secret;
import io.camunda.connector.runtime.core.secret.SecretNotAvailableException;
import io.camunda.connector.runtime.secret.FooBarSecretProvider;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;

class OutboundConnectorExceptionHandlerTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final OutboundConnectorExceptionHandler handler =
      new OutboundConnectorExceptionHandler(new FooBarSecretProvider());

  private final SecretProvider secretProvider = mock(SecretProvider.class);
  private final List<String> requestedKeys = new ArrayList<>();
  private final OutboundConnectorExceptionHandler handlerOverStore =
      new OutboundConnectorExceptionHandler(secretProvider);

  private static ActivatedJob jobWithSecretReference() {
    return jobNaming("{\"value\":\"{{secrets.FOO}}\"}");
  }

  private static ActivatedJob jobNaming(String variables) {
    var job = mock(ActivatedJob.class);
    when(job.getVariables()).thenReturn(variables);
    when(job.getVariablesAsType(ObjectNode.class)).thenReturn(readTree(variables));
    when(job.getKey()).thenReturn(1L);
    when(job.getTenantId()).thenReturn("tenant");
    when(job.getRetries()).thenReturn(3);
    return job;
  }

  private static ObjectNode readTree(String json) {
    try {
      return (ObjectNode) OBJECT_MAPPER.readTree(json);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Answers like the {@link SecretProvider#fetchAll} default over a store holding {@code values}.
   */
  private void holdingOnly(Map<String, String> values) {
    when(secretProvider.fetchAll(any(), any()))
        .thenAnswer(
            invocation -> {
              List<String> keys = invocation.getArgument(0);
              requestedKeys.addAll(keys);
              return keys.stream().map(values::get).filter(Objects::nonNull).toList();
            });
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
  void manageConnectorJobHandlerException_publishesTheReasonTheAllowListCouldNotBeRead() {
    var job = jobWithSecretReference();
    SecretProvider providerThatMustNotBeCalled =
        mock(
            SecretProvider.class,
            invocation -> {
              throw new AssertionError(
                  "an unreadable allow-list means no secret ever reached the input, so there is"
                      + " nothing to resolve for masking");
            });
    var handlerWithGuard = new OutboundConnectorExceptionHandler(providerThatMustNotBeCalled);

    var result =
        handlerWithGuard.manageConnectorJobHandlerException(
            new SecretAllowListUnavailableException(
                "Error retrieving secret keys for element 'Activity_1' in process definition key 42"
                    + " (io.camunda.client.api.command.ProblemException)"),
            job,
            null,
            SecretFilter.allowAll(),
            List.of());

    assertThat(result.exception())
        .hasMessageContaining("Activity_1")
        .hasMessageContaining("ProblemException");
    assertThat(result.retries()).isEqualTo(2);
    assertThat(result.retryBackoff()).isEqualTo(Duration.ofSeconds(5));
  }

  @ParameterizedTest
  @MethodSource("modelBackoffs")
  void manageConnectorJobHandlerException_backsOffTheAllowListReadWhateverTheModelAsksFor(
      Duration modelBackoff) {
    var job = jobWithSecretReference();

    var result =
        handler.manageConnectorJobHandlerException(
            new SecretAllowListUnavailableException("lookup failed"),
            job,
            modelBackoff,
            SecretFilter.allowAll(),
            List.of());

    assertThat(result.retryBackoff()).isEqualTo(Duration.ofSeconds(5));
    assertThat(result.retries()).isEqualTo(2);
  }

  private static Stream<Duration> modelBackoffs() {
    return Stream.of(
        null, Duration.ZERO, Duration.ofSeconds(-1), Duration.ofSeconds(30), Duration.ofMinutes(1));
  }

  @Test
  void manageConnectorJobHandlerException_keepsTheModelsBackoffForOtherMaskingFailures() {
    var job = jobWithSecretReference();
    SecretProvider throwingProvider =
        mock(
            SecretProvider.class,
            invocation -> {
              throw new IllegalStateException("timed out");
            });
    var handlerWithThrowingProvider = new OutboundConnectorExceptionHandler(throwingProvider);

    var result =
        handlerWithThrowingProvider.manageConnectorJobHandlerException(
            new RuntimeException("boom"),
            job,
            Duration.ofSeconds(30),
            SecretFilter.allowAll(),
            List.of());

    assertThat(result.retryBackoff()).isEqualTo(Duration.ofSeconds(30));
  }

  @Test
  void manageConnectorJobHandlerException_publishesTheAllowListFailureRaisedByTheMaskingRead() {
    var job = jobWithSecretReference();
    SecretFilter unreadableAllowList =
        name -> {
          throw new SecretAllowListUnavailableException(
              "Error retrieving secret keys for element 'Activity_1'");
        };

    var result =
        handler.manageConnectorJobHandlerException(
            new RuntimeException("boom"), job, null, unreadableAllowList, List.of());

    assertThat(result.exception()).hasMessageContaining("Activity_1");
    assertThat(result.retries()).isEqualTo(2);
    assertThat(result.retryBackoff()).isEqualTo(Duration.ofSeconds(5));
  }

  @Test
  void aFailedMaskingFetchIsNeverLogged() {
    // A provider writes its own message, and it can carry secret material: AbstractSecretProvider
    // folds a Jackson failure into one, and a bundle that parses as a non-object puts the value it
    // could not coerce into the coercion error.
    var job = jobWithSecretReference();
    when(secretProvider.fetchAll(any(), any()))
        .thenThrow(
            new RuntimeException(
                "Could not resolve secrets: MismatchedInputException: Cannot construct instance of"
                    + " `java.util.LinkedHashMap` from String value ('super-secret')"));

    var logged =
        logsOf(
            () ->
                handlerOverStore.manageConnectorJobHandlerException(
                    new RuntimeException("boom"),
                    job,
                    Duration.ofSeconds(1),
                    SecretFilter.allowAll(),
                    List.of()));

    assertThat(logged).noneMatch(message -> message.contains("super-secret"));
    assertThat(logged).anyMatch(message -> message.contains("java.lang.RuntimeException"));
  }

  @Test
  void aRefusalTheRuntimeWroteItselfKeepsItsMessageInTheLog() {
    // Withholding arbitrary provider text is not a reason to withhold text written to be read: an
    // unreadable allow-list names the element an operator has to look at, and a type name does not.
    var job = jobWithSecretReference();
    SecretFilter unreadableAllowList =
        name -> {
          throw new SecretAllowListUnavailableException(
              "Error retrieving secret keys for element 'Activity_1'");
        };

    var logged =
        logsOf(
            () ->
                handler.manageConnectorJobHandlerException(
                    new RuntimeException("boom"),
                    job,
                    Duration.ofSeconds(1),
                    unreadableAllowList,
                    List.of()));

    assertThat(logged).anyMatch(message -> message.contains("Activity_1"));
  }

  @Test
  void masksALongerSecretThatStartsWithAShorterOne() {
    // Replacing the shorter value first would leave the longer one unmatched and publish its
    // remainder: "x" before "xSUPERSECRET" turns the message into "***SUPERSECRET".
    var job = jobNaming("{\"a\": \"{{secrets.SHORT}}\", \"b\": \"{{secrets.LONG}}\"}");
    holdingOnly(Map.of("SHORT", "x", "LONG", "xSUPERSECRET"));

    var result =
        handlerOverStore.manageConnectorJobHandlerException(
            new RuntimeException("api rejected xSUPERSECRET"),
            job,
            Duration.ofSeconds(1),
            SecretFilter.allowAll(),
            List.of());

    assertThat(requestedKeys).containsExactly("SHORT", "LONG");
    assertThat(result.exception().getMessage()).isEqualTo("api rejected ***");
  }

  @Test
  void anEmptySecretValueDoesNotCorruptTheMessage() {
    // Replacing "" matches at every position, so a provider answering with one would rewrite the
    // whole message into separators rather than redact anything.
    var job = jobNaming("{\"a\": \"{{secrets.BLANK}}\"}");
    holdingOnly(Map.of("BLANK", ""));

    var result =
        handlerOverStore.manageConnectorJobHandlerException(
            new RuntimeException("api rejected the request"),
            job,
            Duration.ofSeconds(1),
            SecretFilter.allowAll(),
            List.of());

    assertThat(result.exception().getMessage()).isEqualTo("api rejected the request");
  }

  @Test
  void aJobDeclaringNoSecretNeverReachesAProvider() {
    // fetchAll is overridable, and one that refuses every batch would otherwise withhold the error
    // message of a job that had nothing to redact in the first place.
    var job = jobNaming("{\"a\": \"nothing to resolve here\"}");
    when(secretProvider.fetchAll(any(), any()))
        .thenThrow(new RuntimeException("store unavailable"));

    var result =
        handlerOverStore.manageConnectorJobHandlerException(
            new RuntimeException("api rejected the request"),
            job,
            Duration.ofSeconds(1),
            SecretFilter.allowAll(),
            List.of());

    verifyNoInteractions(secretProvider);
    assertThat(result.exception().getMessage()).isEqualTo("api rejected the request");
    assertThat(result.retries()).isEqualTo(2);
  }

  @Test
  void withholdsTheMessageWhenTheMaskingReReadComesBackShort() {
    // A name the input declares must have resolved when the input was bound — SecretHandler's
    // replacer throws otherwise — so one missing now means the secret was removed, or access
    // revoked, while the connector ran. fetchAll drops what it cannot resolve, so redacting with
    // what did come back would publish the one that did not in the clear.
    var job = jobNaming("{\"a\": \"{{secrets.FOO}}\", \"b\": \"secrets.BAR\"}");
    holdingOnly(Map.of("FOO", "foo-value"));

    var result =
        handlerOverStore.manageConnectorJobHandlerException(
            new RuntimeException("api rejected bar-value and foo-value"),
            job,
            Duration.ofSeconds(1),
            SecretFilter.allowAll(),
            List.of());

    assertThat(result.responseValue().toString()).doesNotContain("bar-value");
    assertThat(result.exception().getMessage()).doesNotContain("bar-value");
    // Not the input's fault, so the job keeps its remaining attempts.
    assertThat(result.retries()).isEqualTo(2);
    // A count is publishable; it is not something a secret store told this runtime.
    assertThat(result.exception().getMessage()).contains("1 of the 2 secrets");
  }

  @Test
  void publishesTheMessageWhenTheJobFailedBecauseThatSecretHasNoValue() {
    // The one case where a name the input declares is expected back empty: substitution itself
    // threw, so the input never carried BAR's value and there is nothing of it to redact. The
    // message is the replacer's own and names the secret an operator has to go and create —
    // withholding it would replace the answer with a description of the question.
    var job = jobNaming("{\"a\": \"{{secrets.FOO}}\", \"b\": \"secrets.BAR\"}");
    holdingOnly(Map.of("FOO", "foo-value"));

    var result =
        handlerOverStore.manageConnectorJobHandlerException(
            new SecretNotAvailableException(new Secret("BAR", List.of("b"))),
            job,
            Duration.ofSeconds(1),
            SecretFilter.allowAll(),
            List.of());

    assertThat(result.exception().getMessage())
        .isEqualTo("Secret with name 'BAR' is not available");
    assertThat(result.retries()).isZero();
  }

  @Test
  void redactsEveryValueWhenTheMaskingReReadIsComplete() {
    var job = jobNaming("{\"a\": \"{{secrets.FOO}}\", \"b\": \"secrets.BAR\"}");
    holdingOnly(Map.of("FOO", "foo-value", "BAR", "bar-value"));

    var result =
        handlerOverStore.manageConnectorJobHandlerException(
            new RuntimeException("api rejected bar-value and foo-value"),
            job,
            Duration.ofSeconds(1),
            SecretFilter.allowAll(),
            List.of());

    assertThat(result.exception().getMessage()).isEqualTo("api rejected *** and ***");
  }

  @Test
  void aSecretOccurringAtSeveralPathsIsLookedUpOnlyOnce() {
    // Secret now carries fieldPath, so TOKEN at three different paths is three distinct Secret
    // values; the re-read must still collapse them into a single provider lookup by name rather
    // than fetching the same secret once per path it occurs at.
    var job =
        jobNaming(
            "{\"a\": \"{{secrets.TOKEN}}\", \"b\": \"{{secrets.TOKEN}}\", \"c\":"
                + " \"{{secrets.TOKEN}}\"}");
    when(job.getRetries()).thenReturn(3);
    holdingOnly(Map.of("TOKEN", "token-value"));

    var result =
        handlerOverStore.manageConnectorJobHandlerException(
            new RuntimeException("api rejected token-value everywhere"),
            job,
            Duration.ofSeconds(1),
            SecretFilter.allowAll(),
            List.of());

    assertThat(requestedKeys).containsExactly("TOKEN");
    assertThat(result.exception().getMessage()).isEqualTo("api rejected *** everywhere");
  }

  @Test
  void aFailedMaskingFetchIsNeverItselfPublished() {
    // The fetch failure is no safer to publish than the message it was meant to help redact: a
    // provider or client error can echo a response body from the secret store. Nothing built from
    // it can be masked either, since the redaction list is empty by definition on this path.
    var job = jobWithSecretReference();
    when(secretProvider.fetchAll(any(), any()))
        .thenThrow(new RuntimeException("store replied: {\"token\":\"super-secret\"}"));

    var result =
        handlerOverStore.manageConnectorJobHandlerException(
            new RuntimeException("boom"),
            job,
            Duration.ofSeconds(1),
            SecretFilter.allowAll(),
            List.of());

    // Both channels: the payload becomes process variables, and the message becomes the incident
    // message that prepareFailJobCommand sends to Zeebe.
    assertThat(result.responseValue().toString()).doesNotContain("super-secret");
    assertThat(result.exception().getMessage()).doesNotContain("super-secret");
    // The class name is what an operator needs, and carries no request or response data.
    assertThat(result.exception().getMessage()).contains("java.lang.RuntimeException");
  }

  @Test
  void aFailedMaskingFetchDoesNotPublishItsOwnErrorVariables() {
    // exceptionToMap copies a ConnectorException's variables and code into the payload. On this
    // path it would copy them with an empty redaction list, publishing unmasked exactly the data
    // the branch exists to withhold.
    var job = jobWithSecretReference();
    when(secretProvider.fetchAll(any(), any()))
        .thenThrow(
            new ConnectorException(
                "PROVIDER_CODE",
                "lookup rejected",
                null,
                Map.of("response", "credential super-secret was rejected")));

    var result =
        handlerOverStore.manageConnectorJobHandlerException(
            new RuntimeException("boom"),
            job,
            Duration.ofSeconds(1),
            SecretFilter.allowAll(),
            List.of());

    assertThat(result.responseValue().toString())
        .doesNotContain("super-secret")
        .doesNotContain("PROVIDER_CODE");
  }

  @Test
  void handleFinalResultException_reportsAnErrorWhenTheMaskingFetchFails() {
    // The only caller is already inside a catch block, and an exception leaving here escapes it:
    // the job would then be neither completed nor failed, sitting until its activation timeout
    // hands it to another worker, which re-runs a connector that has already run. So a masking
    // fetch that fails has to come back as a result, not as a throw.
    var job = jobWithSecretReference();
    when(secretProvider.fetchAll(any(), any()))
        .thenThrow(new RuntimeException("store unavailable"));

    var result =
        handlerOverStore.handleFinalResultException(
            new RuntimeException("boom"), job, SecretFilter.allowAll(), List.of());

    assertThat(result).isNotNull();
    assertThat(result.retries()).isZero();
  }

  @Test
  void handleFinalResultException_withholdsTheOriginalMessageWhenItCannotBeMasked() {
    // Nothing to mask with means the original message cannot be shown: it may hold a resolved
    // secret, and these variables are visible to anyone who can see the process instance.
    var job = jobWithSecretReference();
    when(secretProvider.fetchAll(any(), any())).thenThrow(new RuntimeException("timed out"));

    var result =
        handlerOverStore.handleFinalResultException(
            new RuntimeException("failed talking to https://api?key=super-secret"),
            job,
            SecretFilter.allowAll(),
            List.of());

    assertThat(result.responseValue().toString()).doesNotContain("super-secret");
    assertThat(result.exception().getMessage()).doesNotContain("super-secret");
  }

  @Test
  void handleFinalResultException_logsTheFailureOnlyAfterRedaction() {
    // The runtime log is a third channel, alongside the payload and the incident message. A
    // connector was handed resolved secrets, so its error message can carry one back; logging it
    // on the way in would put in the log exactly what the other two channels redact.
    var job = jobWithSecretReference();
    when(secretProvider.fetchAll(any(), any())).thenReturn(List.of("super-secret"));

    var logged =
        logsOf(
            () ->
                handlerOverStore.handleFinalResultException(
                    new RuntimeException("failed talking to https://api?key=super-secret"),
                    job,
                    SecretFilter.allowAll(),
                    List.of()));

    assertThat(logged).noneMatch(message -> message.contains("super-secret"));
    assertThat(logged).anyMatch(message -> message.contains("***"));
  }

  @Test
  void handleFinalResultException_logsOnlyTheTypeWhenTheMessageCannotBeRedacted() {
    var job = jobWithSecretReference();
    when(secretProvider.fetchAll(any(), any())).thenThrow(new RuntimeException("timed out"));

    var logged =
        logsOf(
            () ->
                handlerOverStore.handleFinalResultException(
                    new RuntimeException("failed talking to https://api?key=super-secret"),
                    job,
                    SecretFilter.allowAll(),
                    List.of()));

    assertThat(logged).noneMatch(message -> message.contains("super-secret"));
    assertThat(logged).anyMatch(message -> message.contains("java.lang.RuntimeException"));
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
    var job = jobWithSecretReference();
    when(secretProvider.fetchAll(any(), any())).thenReturn(List.of("401"));

    var result =
        handlerOverStore.handleFinalResultException(
            new ConnectorException("401", "Unauthorized"), job, SecretFilter.allowAll(), List.of());

    assertThat(error(result)).containsEntry("code", "401");
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> maskedErrorVariables(Map<String, Object> errorVariables) {
    var job = jobWithSecretReference();
    when(secretProvider.fetchAll(any(), any())).thenReturn(List.of("super-secret"));

    var result =
        handlerOverStore.handleFinalResultException(
            new ConnectorException("401", "Unauthorized", null, errorVariables),
            job,
            SecretFilter.allowAll(),
            List.of());

    return (Map<String, Object>) error(result).get("variables");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> error(ConnectorResult.ErrorResult result) {
    return (Map<String, Object>) ((Map<String, Object>) result.responseValue()).get("error");
  }
}
