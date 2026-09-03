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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.connector.api.secret.SecretContext;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.runtime.core.secret.SecretFilter;
import io.camunda.connector.runtime.secret.FooBarSecretProvider;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;

class OutboundConnectorExceptionHandlerTest {

  private final OutboundConnectorExceptionHandler handler =
      new OutboundConnectorExceptionHandler(new FooBarSecretProvider());

  private static ActivatedJob jobWithSecretReference() {
    var job = mock(ActivatedJob.class);
    when(job.getVariables()).thenReturn("{\"value\":\"{{secrets.FOO}}\"}");
    when(job.getKey()).thenReturn(1L);
    when(job.getTenantId()).thenReturn("tenant");
    when(job.getRetries()).thenReturn(3);
    return job;
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

  private static ActivatedJob jobNaming(String variables) {
    var job = mock(ActivatedJob.class);
    when(job.getVariables()).thenReturn(variables);
    when(job.getKey()).thenReturn(1L);
    when(job.getTenantId()).thenReturn("tenant");
    when(job.getRetries()).thenReturn(3);
    return job;
  }

  /** Captures what the handler's own logger wrote while the given action ran. */
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
  void aFailedMaskingFetchIsNeverLoggedEither() {
    // A provider writes its own message, and it can carry secret material: AbstractSecretProvider
    // folds a Jackson failure into one, and a bundle that parses as a non-object puts the value it
    // could not coerce into the coercion error.
    var job = jobWithSecretReference();
    SecretProvider leakyProvider =
        mock(
            SecretProvider.class,
            invocation -> {
              throw new RuntimeException(
                  "Could not resolve secrets: MismatchedInputException: Cannot construct instance"
                      + " of `java.util.LinkedHashMap` from String value ('super-secret')");
            });
    var handlerWithLeakyProvider = new OutboundConnectorExceptionHandler(leakyProvider);

    var logged =
        logsOf(
            () ->
                handlerWithLeakyProvider.manageConnectorJobHandlerException(
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
    // Withholding arbitrary provider text is not a reason to withhold text written to be read: a
    // refusal names the element an operator has to go and look at, and a type name does not.
    var job = jobWithSecretReference();
    SecretProvider refusingProvider =
        mock(
            SecretProvider.class,
            invocation -> {
              throw new SecretAllowListUnavailableException(
                  "Error retrieving secret keys for element 'Activity_1'");
            });
    var handlerWithRefusingProvider = new OutboundConnectorExceptionHandler(refusingProvider);

    var logged =
        logsOf(
            () ->
                handlerWithRefusingProvider.manageConnectorJobHandlerException(
                    new RuntimeException("boom"),
                    job,
                    Duration.ofSeconds(1),
                    SecretFilter.allowAll(),
                    List.of()));

    assertThat(logged).anyMatch(message -> message.contains("Activity_1"));
  }

  @Test
  void masksALongerSecretThatStartsWithAShorterOne() {
    // Replacing the shorter value first would leave the longer one unmatched and publish its
    // remainder: "x" before "xSUPERSECRET" turns the message into "***SUPERSECRET".
    var job = jobNaming("{\"a\": \"{{secrets.SHORT}}\", \"b\": \"{{secrets.LONG}}\"}");
    var handlerWithBoth =
        new OutboundConnectorExceptionHandler(
            holdingOnly(Map.of("SHORT", "x", "LONG", "xSUPERSECRET")));

    var result =
        handlerWithBoth.manageConnectorJobHandlerException(
            new RuntimeException("api rejected xSUPERSECRET"),
            job,
            Duration.ofSeconds(1),
            SecretFilter.allowAll(),
            List.of());

    assertThat(result.exception().getMessage()).isEqualTo("api rejected ***");
  }

  @Test
  void anEmptySecretValueDoesNotCorruptTheMessage() {
    // Replacing "" matches at every position, so a provider answering with one would rewrite the
    // whole message into separators rather than redact anything.
    var job = jobNaming("{\"a\": \"{{secrets.BLANK}}\"}");
    var handlerWithBlank = new OutboundConnectorExceptionHandler(holdingOnly(Map.of("BLANK", "")));

    var result =
        handlerWithBlank.manageConnectorJobHandlerException(
            new RuntimeException("api rejected the request"),
            job,
            Duration.ofSeconds(1),
            SecretFilter.allowAll(),
            List.of());

    assertThat(result.exception().getMessage()).isEqualTo("api rejected the request");
  }

  @Test
  void aJobDeclaringNoSecretNeverReachesAProvider() {
    // Failing closed is limited to jobs that actually declare a secret, and that is only true if
    // the read is skipped outright rather than delegated to a provider that may refuse every batch.
    var job = jobNaming("{\"a\": \"nothing secret here\"}");
    SecretProvider providerThatMustNotBeCalled =
        mock(
            SecretProvider.class,
            invocation -> {
              throw new AssertionError("a job declaring no secret has nothing to redact");
            });
    var handlerWithGuard = new OutboundConnectorExceptionHandler(providerThatMustNotBeCalled);

    var result =
        handlerWithGuard.manageConnectorJobHandlerException(
            new RuntimeException("api rejected the request"),
            job,
            Duration.ofSeconds(1),
            SecretFilter.allowAll(),
            List.of());

    assertThat(result.exception().getMessage()).isEqualTo("api rejected the request");
  }

  /** A provider holding exactly the given names, and answering {@code fetchAll} in key order. */
  private static SecretProvider holdingOnly(Map<String, String> secrets) {
    return new SecretProvider() {
      @Override
      public String getSecret(String name, SecretContext context) {
        return secrets.get(name);
      }

      @Override
      public List<String> fetchAll(List<String> keys, SecretContext context) {
        return keys.stream().map(secrets::get).filter(Objects::nonNull).toList();
      }
    };
  }
}
