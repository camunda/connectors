/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.util.retry;

import static io.camunda.connector.agenticai.util.retry.CamundaApiRetry.FailureReason.INTERRUPTED;
import static io.camunda.connector.agenticai.util.retry.CamundaApiRetry.FailureReason.PERMANENT_ERROR;
import static io.camunda.connector.agenticai.util.retry.CamundaApiRetry.FailureReason.RETRIES_EXHAUSTED;
import static io.camunda.connector.agenticai.util.retry.ErrorClassifier.Decision.PERMANENT;
import static io.camunda.connector.agenticai.util.retry.ErrorClassifier.Decision.RETRYABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.agenticai.util.retry.CamundaApiRetry.FailureReason;
import io.camunda.connector.api.error.ConnectorException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CamundaApiRetryTest {

  @AfterEach
  void clearInterruptFlag() {
    Thread.interrupted();
  }

  // Helper: builds a FailureMapper that captures call arguments for assertion
  private static CamundaApiRetry.FailureMapper simpleMapper() {
    return (cause, attempt, reason) ->
        new ConnectorException("TEST", "test-" + reason + "-" + attempt, cause);
  }

  // Helper: no-op sleeper (never throws)
  private static CamundaApiRetry.Sleeper recordingSleeper(List<Duration> recorded) {
    return d -> recorded.add(d);
  }

  @Test
  void successOnFirstAttempt_returnsValue() {
    final List<Duration> recordedSleeps = new ArrayList<>();

    final String result =
        CamundaApiRetry.execute(
            () -> "ok",
            ErrorClassifier.onAllExceptions(),
            3,
            Duration.ofSeconds(1),
            simpleMapper(),
            recordingSleeper(recordedSleeps));

    assertThat(result).isEqualTo("ok");
    assertThat(recordedSleeps).isEmpty();
  }

  @Test
  void retryableFailuresThenSuccess_returnsValueAndRecordsSleeps() {
    final List<Duration> recordedSleeps = new ArrayList<>();
    final AtomicInteger callCount = new AtomicInteger(0);

    final String result =
        CamundaApiRetry.execute(
            () -> {
              if (callCount.incrementAndGet() <= 2) {
                throw new RuntimeException("transient");
              }
              return "success";
            },
            ErrorClassifier.onAllExceptions(),
            4,
            Duration.ofSeconds(1),
            simpleMapper(),
            recordingSleeper(recordedSleeps));

    assertThat(result).isEqualTo("success");
    assertThat(recordedSleeps).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2));
  }

  @Test
  void permanentError_throwsImmediatelyWithNoSleep() {
    final List<Duration> recordedSleeps = new ArrayList<>();
    final AtomicInteger[] capturedAttempt = {null};
    final FailureReason[] capturedReason = {null};

    final CamundaApiRetry.FailureMapper capturingMapper =
        (cause, attempt, reason) -> {
          capturedAttempt[0] = new AtomicInteger(attempt);
          capturedReason[0] = reason;
          return new ConnectorException("TEST", "test-" + reason + "-" + attempt, cause);
        };

    assertThatThrownBy(
            () ->
                CamundaApiRetry.execute(
                    () -> {
                      throw new RuntimeException("permanent");
                    },
                    t -> PERMANENT,
                    3,
                    Duration.ofSeconds(1),
                    capturingMapper,
                    recordingSleeper(recordedSleeps)))
        .isInstanceOf(ConnectorException.class);

    assertThat(capturedReason[0]).isEqualTo(PERMANENT_ERROR);
    assertThat(capturedAttempt[0].get()).isEqualTo(1);
    assertThat(recordedSleeps).isEmpty();
  }

  @Test
  void permanentErrorAfterRetries_throwsWithCorrectAttempt() {
    final List<Duration> recordedSleeps = new ArrayList<>();
    final AtomicInteger callCount = new AtomicInteger(0);
    final AtomicInteger[] capturedAttempt = {null};
    final FailureReason[] capturedReason = {null};

    final CamundaApiRetry.FailureMapper capturingMapper =
        (cause, attempt, reason) -> {
          capturedAttempt[0] = new AtomicInteger(attempt);
          capturedReason[0] = reason;
          return new ConnectorException("TEST", "test-" + reason + "-" + attempt, cause);
        };

    assertThatThrownBy(
            () ->
                CamundaApiRetry.execute(
                    () -> {
                      throw new RuntimeException("fail");
                    },
                    t -> {
                      int attempt = callCount.incrementAndGet();
                      return attempt <= 2 ? RETRYABLE : PERMANENT;
                    },
                    4,
                    Duration.ofSeconds(1),
                    capturingMapper,
                    recordingSleeper(recordedSleeps)))
        .isInstanceOf(ConnectorException.class);

    assertThat(capturedReason[0]).isEqualTo(PERMANENT_ERROR);
    assertThat(capturedAttempt[0].get()).isEqualTo(3);
    assertThat(recordedSleeps).hasSize(2);
  }

  @Test
  void allRetriesExhausted_throwsWithRetriesExhaustedReason() {
    final List<Duration> recordedSleeps = new ArrayList<>();
    final AtomicInteger[] capturedAttempt = {null};
    final FailureReason[] capturedReason = {null};

    final CamundaApiRetry.FailureMapper capturingMapper =
        (cause, attempt, reason) -> {
          capturedAttempt[0] = new AtomicInteger(attempt);
          capturedReason[0] = reason;
          return new ConnectorException("TEST", "test-" + reason + "-" + attempt, cause);
        };

    assertThatThrownBy(
            () ->
                CamundaApiRetry.execute(
                    () -> {
                      throw new RuntimeException("always fail");
                    },
                    ErrorClassifier.onAllExceptions(),
                    4,
                    Duration.ofSeconds(1),
                    capturingMapper,
                    recordingSleeper(recordedSleeps)))
        .isInstanceOf(ConnectorException.class);

    assertThat(capturedReason[0]).isEqualTo(RETRIES_EXHAUSTED);
    assertThat(capturedAttempt[0].get()).isEqualTo(5);
    assertThat(recordedSleeps)
        .containsExactly(
            Duration.ofSeconds(1),
            Duration.ofSeconds(2),
            Duration.ofSeconds(4),
            Duration.ofSeconds(8));
  }

  @Test
  void interruptedSleep_throwsWithInterruptedReasonAndRestoresFlag() {
    final FailureReason[] capturedReason = {null};
    final Throwable[] capturedCause = {null};

    final CamundaApiRetry.FailureMapper capturingMapper =
        (cause, attempt, reason) -> {
          capturedReason[0] = reason;
          capturedCause[0] = cause;
          return new ConnectorException("TEST", "test-" + reason + "-" + attempt, cause);
        };

    final CamundaApiRetry.Sleeper interruptingSleeper =
        d -> {
          throw new InterruptedException("interrupted");
        };

    assertThatThrownBy(
            () ->
                CamundaApiRetry.execute(
                    () -> {
                      throw new RuntimeException("fail");
                    },
                    ErrorClassifier.onAllExceptions(),
                    3,
                    Duration.ofSeconds(1),
                    capturingMapper,
                    interruptingSleeper))
        .isInstanceOf(ConnectorException.class);

    assertThat(capturedReason[0]).isEqualTo(INTERRUPTED);
    assertThat(capturedCause[0]).isInstanceOf(InterruptedException.class);
    assertThat(Thread.currentThread().isInterrupted()).isTrue();
    // Clean up interrupt flag
    Thread.interrupted();
  }

  @Test
  void onAllExceptions_classifiesEveryExceptionAsRetryable() {
    final ErrorClassifier classifier = ErrorClassifier.onAllExceptions();

    // execute() catches Exception, not Throwable — these are the types that reach the classifier
    assertThat(classifier.classify(new RuntimeException())).isEqualTo(RETRYABLE);
    assertThat(classifier.classify(new Exception())).isEqualTo(RETRYABLE);
    assertThat(classifier.classify(new java.io.IOException())).isEqualTo(RETRYABLE);
  }

  @Test
  void delaySequence_followsExponentialBackoff() {
    final List<Duration> recordedSleeps = new ArrayList<>();

    assertThatThrownBy(
        () ->
            CamundaApiRetry.execute(
                () -> {
                  throw new RuntimeException("always fail");
                },
                ErrorClassifier.onAllExceptions(),
                4,
                Duration.ofSeconds(1),
                simpleMapper(),
                recordingSleeper(recordedSleeps)));

    assertThat(recordedSleeps)
        .containsExactly(
            Duration.ofSeconds(1),
            Duration.ofSeconds(2),
            Duration.ofSeconds(4),
            Duration.ofSeconds(8));
  }
}
