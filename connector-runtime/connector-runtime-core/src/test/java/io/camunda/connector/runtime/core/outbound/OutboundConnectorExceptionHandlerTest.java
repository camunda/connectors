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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.connector.api.error.ConnectorExceptionBuilder;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.error.ConnectorRetryExceptionBuilder;
import io.camunda.connector.api.secret.SecretContext;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.runtime.core.FooBarSecretProvider;
import io.camunda.connector.runtime.core.secret.SecretAllowListUnavailableException;
import io.camunda.connector.runtime.core.secret.SecretFilter;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class OutboundConnectorExceptionHandlerTest {

  private final OutboundConnectorExceptionHandler handler =
      new OutboundConnectorExceptionHandler(new FooBarSecretProvider());

  private static ActivatedJob jobWithSecretReference() {
    return jobNaming("{\"value\":\"{{secrets.FOO}}\"}");
  }

  private static ActivatedJob jobNaming(String variables) {
    var job = mock(ActivatedJob.class);
    when(job.getVariables()).thenReturn(variables);
    when(job.getKey()).thenReturn(1L);
    when(job.getTenantId()).thenReturn("tenant");
    when(job.getRetries()).thenReturn(3);
    return job;
  }

  private static SecretProvider holdingOnly(Map<String, String> secrets) {
    return new SecretProvider() {
      @Override
      public String getSecret(String name, SecretContext context) {
        return secrets.get(name);
      }
    };
  }

  @Test
  void handleFinalResultException_secretFilterThrows_returnsSanitizedResultInsteadOfPropagating() {
    var job = jobWithSecretReference();
    SecretFilter throwingFilter =
        name -> {
          throw new IllegalStateException("process-definition lookup failed");
        };

    var result =
        handler.handleFinalResultException(new RuntimeException("boom"), job, throwingFilter);

    assertThat(result.retries()).isEqualTo(2);
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
            new RuntimeException("boom"), job, SecretFilter.allowAll());

    assertThat(result.retries()).isEqualTo(2);
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
                    + " (io.camunda.operate.exception.OperateException)"),
            job,
            null,
            SecretFilter.allowAll());

    assertThat(result.exception())
        .hasMessageContaining("Activity_1")
        .hasMessageContaining("OperateException");
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
            SecretFilter.allowAll());

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
            new RuntimeException("boom"), job, Duration.ofSeconds(30), SecretFilter.allowAll());

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
            new RuntimeException("boom"), job, null, unreadableAllowList);

    assertThat(result.exception()).hasMessageContaining("Activity_1");
    assertThat(result.retries()).isEqualTo(2);
    assertThat(result.retryBackoff()).isEqualTo(Duration.ofSeconds(5));
  }

  @Test
  void masksALongerSecretThatStartsWithAShorterOne() {
    // Replacing the shorter value first would leave the longer one unmatched and publish its
    // remainder: "x" before "xSUPERSECRET" turns the message into "***SUPERSECRET".
    var job = jobNaming("{\"a\": \"{{secrets.SHORT}}\", \"b\": \"{{secrets.LONG}}\"}");
    var handlerHoldingBoth =
        new OutboundConnectorExceptionHandler(
            holdingOnly(Map.of("SHORT", "x", "LONG", "xSUPERSECRET")));

    var result =
        handlerHoldingBoth.manageConnectorJobHandlerException(
            new RuntimeException("api rejected xSUPERSECRET"),
            job,
            Duration.ofSeconds(1),
            SecretFilter.allowAll());

    assertThat(result.exception().getMessage()).isEqualTo("api rejected ***");
  }

  @Test
  void anEmptySecretValueDoesNotCorruptTheMessage() {
    // Replacing "" matches at every position, so a provider answering with one would rewrite the
    // whole message into separators rather than redact anything.
    var job = jobNaming("{\"a\": \"{{secrets.BLANK}}\"}");
    var handlerHoldingABlank =
        new OutboundConnectorExceptionHandler(holdingOnly(Map.of("BLANK", "")));

    var result =
        handlerHoldingABlank.manageConnectorJobHandlerException(
            new RuntimeException("api rejected the request"),
            job,
            Duration.ofSeconds(1),
            SecretFilter.allowAll());

    assertThat(result.exception().getMessage()).isEqualTo("api rejected the request");
  }

  @Test
  void aJobDeclaringNoSecretNeverReachesAProvider() {
    // fetchAll is overridable, and one that refuses every batch would otherwise withhold the error
    // message of a job that had nothing to redact in the first place.
    var job = jobNaming("{\"a\": \"nothing to resolve here\"}");
    SecretProvider providerThatMustNotBeCalled =
        mock(
            SecretProvider.class,
            invocation -> {
              throw new AssertionError("a job declaring no secret has nothing to resolve");
            });
    var handlerWithGuard = new OutboundConnectorExceptionHandler(providerThatMustNotBeCalled);

    var result =
        handlerWithGuard.manageConnectorJobHandlerException(
            new RuntimeException("api rejected the request"),
            job,
            Duration.ofSeconds(1),
            SecretFilter.allowAll());

    assertThat(result.exception().getMessage()).isEqualTo("api rejected the request");
    assertThat(result.retries()).isEqualTo(2);
  }
}
