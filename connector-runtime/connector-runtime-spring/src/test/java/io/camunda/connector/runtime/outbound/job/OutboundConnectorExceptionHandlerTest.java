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
import io.camunda.connector.api.error.ConnectorExceptionBuilder;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.error.ConnectorRetryExceptionBuilder;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.runtime.core.secret.SecretFilter;
import io.camunda.connector.runtime.secret.FooBarSecretProvider;
import java.time.Duration;
import java.util.ArrayList;
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

  private final SecretProvider maskingStore = mock(SecretProvider.class);
  private final List<String> requestedKeys = new ArrayList<>();
  private final OutboundConnectorExceptionHandler handlerOverMaskingStore =
      new OutboundConnectorExceptionHandler(maskingStore);

  private static ActivatedJob jobWithSecretReference() {
    var job = mock(ActivatedJob.class);
    var variables = "{\"value\":\"{{secrets.FOO}}\"}";
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

  @Test
  void handleFinalResultException_secretFilterThrows_returnsSanitizedResultInsteadOfPropagating() {
    var job = jobWithSecretReference();
    SecretFilter throwingFilter =
        name -> {
          throw new IllegalStateException("process-definition lookup failed");
        };

    var result =
        handler.handleFinalResultException(
            new RuntimeException("boom"), job, throwingFilter, List.of());

    assertThat(result.retries()).isZero();
    assertThat(result.exception().getMessage()).contains("Fetching secrets failed");
  }

  @Test
  void
      handleFinalResultException_secretProviderThrows_returnsSanitizedResultInsteadOfPropagating() {
    var job = jobWithSecretReference();
    SecretProvider throwingProvider =
        mock(
            SecretProvider.class,
            invocation -> {
              throw new IllegalStateException("secret store unavailable");
            });
    var handlerWithThrowingProvider = new OutboundConnectorExceptionHandler(throwingProvider);

    var result =
        handlerWithThrowingProvider.handleFinalResultException(
            new RuntimeException("boom"), job, SecretFilter.allowAll(), List.of());

    assertThat(result.retries()).isZero();
    assertThat(result.exception().getMessage()).contains("Fetching secrets failed");
  }

  @Test
  void handleFinalResultException_twoArgOverload_withholdsMessageWithoutTouchingTheProvider() {
    var job = jobWithSecretReference();
    SecretProvider providerThatMustNotBeCalled =
        mock(
            SecretProvider.class,
            invocation -> {
              throw new AssertionError(
                  "this legacy overload has no filter and must not resolve any secret");
            });
    var handlerWithGuard = new OutboundConnectorExceptionHandler(providerThatMustNotBeCalled);

    var result = handlerWithGuard.handleFinalResultException(new RuntimeException("boom"), job);

    assertThat(result.retries()).isEqualTo(0);
    assertThat(result.exception().getMessage()).doesNotContain("boom");
  }

  @Test
  void
      manageConnectorJobHandlerException_threeArgOverload_withholdsMessageWithoutTouchingTheProvider() {
    var job = jobWithSecretReference();
    SecretProvider providerThatMustNotBeCalled =
        mock(
            SecretProvider.class,
            invocation -> {
              throw new AssertionError(
                  "this legacy overload has no filter and must not resolve any secret");
            });
    var handlerWithGuard = new OutboundConnectorExceptionHandler(providerThatMustNotBeCalled);

    var result =
        handlerWithGuard.manageConnectorJobHandlerException(
            new RuntimeException("boom"), job, Duration.ofSeconds(1));

    assertThat(result.retries()).isEqualTo(2);
    assertThat(result.exception().getMessage()).doesNotContain("boom");
  }

  @Test
  void
      manageConnectorJobHandlerException_threeArgOverload_neverExposesTheOriginalExceptionsVariables() {
    var job = jobWithSecretReference();
    var connectorException =
        new ConnectorExceptionBuilder()
            .errorCode(FooBarSecretProvider.SECRET_VALUE)
            .message("original response body: " + FooBarSecretProvider.SECRET_VALUE)
            .errorVariables(Map.of("responseBody", FooBarSecretProvider.SECRET_VALUE))
            .build();

    var result = handler.manageConnectorJobHandlerException(connectorException, job, null);

    @SuppressWarnings("unchecked")
    var errorPayload =
        (Map<String, Object>) ((Map<String, Object>) result.responseValue()).get("error");
    assertThat(errorPayload).doesNotContainKey("variables");
    assertThat(errorPayload).doesNotContainKey("code");
    assertThat(errorPayload.get("message").toString())
        .doesNotContain(FooBarSecretProvider.SECRET_VALUE);
    assertThat(result.exception().getMessage()).doesNotContain(FooBarSecretProvider.SECRET_VALUE);
    assertThat(errorPayload.get("type")).isEqualTo(connectorException.getClass().getName());
  }

  @Test
  void
      manageConnectorJobHandlerException_threeArgOverload_honorsConnectorRetryExceptionConfiguration()
          throws Exception {
    var job = jobWithSecretReference();
    var retryException =
        new ConnectorRetryExceptionBuilder()
            .message("boom")
            .retries(7)
            .backoffDuration(Duration.ofMinutes(5))
            .build();

    var result =
        handler.manageConnectorJobHandlerException(retryException, job, Duration.ofSeconds(1));

    assertThat(result.retries()).isEqualTo(7);
    assertThat(result.retryBackoff()).isEqualTo(Duration.ofMinutes(5));
  }

  @Test
  void
      manageConnectorJobHandlerException_threeArgOverload_zeroesRetriesForConnectorInputException() {
    var job = jobWithSecretReference();

    var result =
        handler.manageConnectorJobHandlerException(
            new ConnectorInputException("bad input", new RuntimeException()),
            job,
            Duration.ofSeconds(1));

    assertThat(result.retries()).isEqualTo(0);
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
    var job = jobNaming("{\"a\": \"{{secrets.FOO}}\"}");
    when(job.getRetries()).thenReturn(3);
    when(maskingStore.fetchAll(any(), any()))
        .thenThrow(
            new RuntimeException(
                "Could not resolve secrets: MismatchedInputException: Cannot construct instance of"
                    + " `java.util.LinkedHashMap` from String value ('super-secret')"));

    var logged =
        logsOf(
            () ->
                handlerOverMaskingStore.manageConnectorJobHandlerException(
                    new RuntimeException("boom"),
                    job,
                    Duration.ofSeconds(1),
                    SecretFilter.allowAll(),
                    List.of()));

    assertThat(logged).noneMatch(message -> message.contains("super-secret"));
    assertThat(logged).anyMatch(message -> message.contains("java.lang.RuntimeException"));
  }

  @Test
  void masksALongerSecretThatStartsWithAShorterOne() {
    // Replacing the shorter value first would leave the longer one unmatched and publish its
    // remainder: "x" before "xSUPERSECRET" turns the message into "***SUPERSECRET".
    var job = jobNaming("{\"a\": \"{{secrets.SHORT}}\", \"b\": \"{{secrets.LONG}}\"}");
    when(job.getRetries()).thenReturn(3);
    holdingOnly(Map.of("SHORT", "x", "LONG", "xSUPERSECRET"));

    var result =
        handlerOverMaskingStore.manageConnectorJobHandlerException(
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
    when(job.getRetries()).thenReturn(3);
    holdingOnly(Map.of("BLANK", ""));

    var result =
        handlerOverMaskingStore.manageConnectorJobHandlerException(
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
    when(job.getRetries()).thenReturn(3);
    when(maskingStore.fetchAll(any(), any())).thenThrow(new RuntimeException("store unavailable"));

    var result =
        handlerOverMaskingStore.manageConnectorJobHandlerException(
            new RuntimeException("api rejected the request"),
            job,
            Duration.ofSeconds(1),
            SecretFilter.allowAll(),
            List.of());

    verifyNoInteractions(maskingStore);
    assertThat(result.exception().getMessage()).isEqualTo("api rejected the request");
    assertThat(result.retries()).isEqualTo(2);
  }

  @Test
  void aFailedMaskingFetchNeverReachesTheIncident() {
    // The provider's own exception is as unsafe to publish as the message it was meant to help
    // redact, and on this path there is nothing to redact it with.
    var job = jobNaming("{\"a\": \"{{secrets.FOO}}\"}");
    when(job.getRetries()).thenReturn(3);
    when(maskingStore.fetchAll(any(), any()))
        .thenThrow(
            new ConnectorExceptionBuilder()
                .errorCode("super-secret")
                .message("store rejected the bundle: super-secret")
                .errorVariables(Map.of("responseBody", "super-secret"))
                .build());

    var result =
        handlerOverMaskingStore.manageConnectorJobHandlerException(
            new RuntimeException("api rejected super-secret"),
            job,
            Duration.ofSeconds(1),
            SecretFilter.allowAll(),
            List.of());

    assertThat(result.exception())
        .hasMessageStartingWith("Fetching secrets failed, original error can't be displayed")
        .hasMessageNotContaining("super-secret")
        .hasNoCause();
    assertThat(errorPayload(result)).doesNotContainKeys("variables", "code");
    assertThat(errorPayload(result).toString()).doesNotContain("super-secret");
  }

  @Test
  void aFailedMaskingFetchNeverReachesTheIncidentOfAFinalResultFailure() {
    var job = jobNaming("{\"a\": \"{{secrets.FOO}}\"}");
    when(job.getRetries()).thenReturn(3);
    when(maskingStore.fetchAll(any(), any()))
        .thenThrow(
            new ConnectorExceptionBuilder()
                .errorCode("super-secret")
                .message("store rejected the bundle: super-secret")
                .errorVariables(Map.of("responseBody", "super-secret"))
                .build());

    var result =
        handlerOverMaskingStore.handleFinalResultException(
            new RuntimeException("api rejected super-secret"),
            job,
            SecretFilter.allowAll(),
            List.of());

    assertThat(result.exception())
        .hasMessageStartingWith("Fetching secrets failed, original error can't be displayed")
        .hasMessageNotContaining("super-secret")
        .hasNoCause();
    assertThat(errorPayload(result)).doesNotContainKeys("variables", "code");
    assertThat(errorPayload(result).toString()).doesNotContain("super-secret");
  }

  @Test
  void aFinalResultFailureIsNotRetriedWhenTheValuesToRedactItWithCannotBeRead() {
    // Reaching here means the connector has already run, so a retry would repeat its side effects
    // — whether or not the message could be redacted.
    var job = jobNaming("{\"a\": \"{{secrets.FOO}}\"}");
    when(job.getRetries()).thenReturn(3);
    when(maskingStore.fetchAll(any(), any())).thenThrow(new RuntimeException("timed out"));

    var result =
        handlerOverMaskingStore.handleFinalResultException(
            new RuntimeException("boom"), job, SecretFilter.allowAll(), List.of());

    assertThat(result.retries()).isZero();
  }

  @Test
  void aJobIsNotRetriedWhenTheMaskingReadSaysTheInputCanNeverBind() {
    // A provider that refuses every lookup throws for the masking read too; retrying will not
    // change that, so it must not be reported as transient.
    var job = jobNaming("{\"a\": \"{{secrets.FOO}}\"}");
    when(job.getRetries()).thenReturn(3);
    when(maskingStore.fetchAll(any(), any()))
        .thenThrow(new ConnectorInputException("secret 'FOO' was not resolved", null));

    var result =
        handlerOverMaskingStore.manageConnectorJobHandlerException(
            new RuntimeException("boom"),
            job,
            Duration.ofSeconds(1),
            SecretFilter.allowAll(),
            List.of());

    assertThat(result.retries()).isZero();
  }

  @Test
  void aJobThatCanNeverBindStaysFatalWhenTheMaskingReadOnlyFailedTransiently() {
    // The two failures are independent: the job's own can be permanent while reading the values to
    // redact it merely times out. Classifying from the masking failure alone would hand a job that
    // can never bind its remaining attempts back.
    var job = jobNaming("{\"a\": \"{{secrets.FOO}}\"}");
    when(job.getRetries()).thenReturn(3);
    when(maskingStore.fetchAll(any(), any())).thenThrow(new RuntimeException("timed out"));

    var result =
        handlerOverMaskingStore.manageConnectorJobHandlerException(
            new ConnectorInputException("bad input", null),
            job,
            Duration.ofSeconds(1),
            SecretFilter.allowAll(),
            List.of());

    assertThat(result.retries()).isZero();
  }

  @Test
  void aJobKeepsItsAttemptsWhenNeitherFailureBlamesTheInput() {
    var job = jobNaming("{\"a\": \"{{secrets.FOO}}\"}");
    when(job.getRetries()).thenReturn(3);
    when(maskingStore.fetchAll(any(), any())).thenThrow(new RuntimeException("timed out"));

    var result =
        handlerOverMaskingStore.manageConnectorJobHandlerException(
            new RuntimeException("boom"),
            job,
            Duration.ofSeconds(1),
            SecretFilter.allowAll(),
            List.of());

    assertThat(result.retries()).isEqualTo(2);
  }

  @Test
  void aMaskingReadThatComesBackShortWithholdsTheMessage() {
    // The default fetchAll drops names it cannot resolve, so a partial answer leaves a value the
    // connector held out of the redaction list: the message may still carry it.
    var job = jobNaming("{\"a\": \"{{secrets.FOO}}\", \"b\": \"{{secrets.ROTATED}}\"}");
    when(job.getRetries()).thenReturn(3);
    holdingOnly(Map.of("FOO", "readable"));

    var result =
        handlerOverMaskingStore.manageConnectorJobHandlerException(
            new RuntimeException("api rejected super-secret"),
            job,
            Duration.ofSeconds(1),
            SecretFilter.allowAll(),
            List.of());

    assertThat(result.exception())
        .hasMessageStartingWith("Fetching secrets failed, original error can't be displayed")
        .hasMessageNotContaining("super-secret")
        .hasNoCause();
    assertThat(result.retries()).isEqualTo(2);
  }

  @Test
  void aMaskingReadThatComesBackShortIsAcceptedWhenTheJobFailedForThatSameSecret() {
    // The job already failed because the secret has no value, so the short read reports nothing
    // new — withholding the message here would hide the very error the operator needs.
    var job = jobNaming("{\"a\": \"{{secrets.FOO}}\", \"b\": \"{{secrets.MISSING}}\"}");
    when(job.getRetries()).thenReturn(3);
    holdingOnly(Map.of("FOO", "readable"));

    var result =
        handlerOverMaskingStore.manageConnectorJobHandlerException(
            new ConnectorInputException("Secret with name 'MISSING' is not available", null),
            job,
            Duration.ofSeconds(1),
            SecretFilter.allowAll(),
            List.of());

    assertThat(result.exception())
        .hasMessage("Secret with name 'MISSING' is not available")
        .hasMessageNotContaining("Fetching secrets failed");
  }

  @Test
  void theErrorVariablesOfAConnectorExceptionAreMasked() {
    // For HTTP connectors these carry the full response body and headers, so an API echoing a
    // rejected credential back would otherwise publish it as a process variable.
    var job = jobNaming("{\"a\": \"{{secrets.FOO}}\"}");
    when(job.getRetries()).thenReturn(3);
    holdingOnly(Map.of("FOO", "super-secret"));
    var connectorException =
        new ConnectorExceptionBuilder()
            .errorCode("401")
            .message("Unauthorized")
            .errorVariables(
                Map.of(
                    "response",
                    Map.of(
                        "body",
                        List.of("token super-secret rejected"),
                        "headers",
                        Map.of("Authorization", "Bearer super-secret"))))
            .build();

    var result =
        handlerOverMaskingStore.handleFinalResultException(
            connectorException, job, SecretFilter.allowAll(), List.of());

    var payload = errorPayload(result);
    assertThat(payload.get("variables").toString()).doesNotContain("super-secret");
    assertThat(payload.get("variables").toString()).contains("***");
    // boundary events match on the code, so it is published as raised
    assertThat(payload).containsEntry("code", "401");
  }

  @Test
  void theErrorVariablesOfAConnectorExceptionAreMaskedWithBindTimeCapturesToo() {
    // The value rotated after the input was bound, so it no longer reads back — the variables
    // still carry the value the connector actually sent.
    var job = jobNaming("{\"a\": \"{{secrets.FOO}}\"}");
    when(job.getRetries()).thenReturn(3);
    holdingOnly(Map.of("FOO", "rotated-value"));
    var connectorException =
        new ConnectorExceptionBuilder()
            .errorCode("401")
            .message("Unauthorized")
            .errorVariables(Map.of("responseBody", "token bound-value rejected"))
            .build();

    var result =
        handlerOverMaskingStore.handleFinalResultException(
            connectorException, job, SecretFilter.allowAll(), List.of("bound-value"));

    assertThat(errorPayload(result).get("variables").toString()).doesNotContain("bound-value");
  }

  @Test
  void aSecretOccurringAtSeveralPathsIsLookedUpOnlyOnce() {
    var job =
        jobNaming(
            "{\"a\": \"{{secrets.TOKEN}}\", \"b\": \"{{secrets.TOKEN}}\", \"c\":"
                + " \"{{secrets.TOKEN}}\"}");
    when(job.getRetries()).thenReturn(3);
    holdingOnly(Map.of("TOKEN", "token-value"));

    var result =
        handlerOverMaskingStore.manageConnectorJobHandlerException(
            new RuntimeException("api rejected token-value everywhere"),
            job,
            Duration.ofSeconds(1),
            SecretFilter.allowAll(),
            List.of());

    assertThat(requestedKeys).containsExactly("TOKEN");
    assertThat(result.exception().getMessage()).isEqualTo("api rejected *** everywhere");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> errorPayload(
      io.camunda.connector.runtime.core.outbound.ConnectorResult.ErrorResult result) {
    return (Map<String, Object>) ((Map<String, Object>) result.responseValue()).get("error");
  }

  private static ActivatedJob jobNaming(String variables) {
    var job = mock(ActivatedJob.class);
    when(job.getVariables()).thenReturn(variables);
    when(job.getVariablesAsType(ObjectNode.class)).thenReturn(readTree(variables));
    when(job.getKey()).thenReturn(1L);
    when(job.getTenantId()).thenReturn("tenant");
    return job;
  }

  /**
   * Answers like the {@link SecretProvider#fetchAll} default over a store holding {@code values}.
   */
  private void holdingOnly(Map<String, String> values) {
    when(maskingStore.fetchAll(any(), any()))
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
}
