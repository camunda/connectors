/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.managedscript;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.managedscript.api.ErrorCode;
import io.camunda.connector.managedscript.api.ProvisioningException;
import io.camunda.connector.managedscript.api.ScriptLanguage;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Time is a fake clock, except for the backoff between attempts, which is a few milliseconds. */
class ExecutorReadinessTest {

  private static final Duration BACKOFF = Duration.ofMillis(5);
  private static final Duration WINDOW = Duration.ofMinutes(2);

  private final AtomicLong now = new AtomicLong();
  private ExecutorReadiness readiness;

  @AfterEach
  void close() {
    if (readiness != null) {
      readiness.close();
    }
  }

  @Test
  void measuresTheGrantPropagationWindowFromTheFirstAttemptOnly() {
    final var provider = denying();
    readiness = new ExecutorReadiness(provider, BACKOFF, WINDOW, now::get);
    final var first = readiness.start(ScriptLanguage.PYTHON);
    assertThat(first).isNotDone();

    now.addAndGet(WINDOW.toNanos());
    assertThat(failureOf(first).permissionDenied()).isTrue();

    // A new attempt after the backoff, or after a missing executor, gets no new window.
    now.addAndGet(BACKOFF.toNanos());
    assertThat(failureOf(readiness.start(ScriptLanguage.PYTHON)).code())
        .isEqualTo(ErrorCode.PLATFORM_REJECTED);
    assertThat(provider.ensureCalls()).isGreaterThan(1);
  }

  @Test
  void doesNotReportADenialWithinTheWindowAsALastingFailure() {
    final var provider = denying();
    readiness = new ExecutorReadiness(provider, BACKOFF, WINDOW, now::get);
    readiness.start(ScriptLanguage.PYTHON);
    awaitCalls(provider, 2);

    now.addAndGet(Duration.ofMinutes(1).toNanos());

    assertThat(readiness.lastingFailure(ScriptLanguage.PYTHON, Duration.ofSeconds(10))).isEmpty();
  }

  @Test
  void reportsARetryableFailureOnlyOnceItLasts() {
    final var provider =
        new FakeProvider() {
          @Override
          public void ensureExecutors(ScriptLanguage language) {
            super.ensureExecutors(language);
            throw new ProvisioningException(ErrorCode.PLATFORM_THROTTLED, "slow down");
          }
        };
    readiness = new ExecutorReadiness(provider, BACKOFF, WINDOW, now::get);
    readiness.start(ScriptLanguage.JAVASCRIPT);
    awaitCalls(provider, 2);

    assertThat(readiness.lastingFailure(ScriptLanguage.JAVASCRIPT, Duration.ofSeconds(10)))
        .isEmpty();
    now.addAndGet(Duration.ofSeconds(11).toNanos());
    assertThat(readiness.lastingFailure(ScriptLanguage.JAVASCRIPT, Duration.ofSeconds(10)))
        .hasValueSatisfying(e -> assertThat(e.code()).isEqualTo(ErrorCode.PLATFORM_THROTTLED));
    assertThat(readiness.lastingFailure(ScriptLanguage.PYTHON, Duration.ofSeconds(10))).isEmpty();
  }

  @Test
  void restartsOnlyTheAttemptTheJobWaitedFor() throws Exception {
    final var provider = new FakeProvider();
    readiness = new ExecutorReadiness(provider, BACKOFF, WINDOW, now::get);
    final var first = readiness.start(ScriptLanguage.PYTHON);
    first.get(5, TimeUnit.SECONDS);

    final var second = readiness.restart(ScriptLanguage.PYTHON, first);
    second.get(5, TimeUnit.SECONDS);
    // A job that waited for the first attempt too finds the executor made ready again.
    final var third = readiness.restart(ScriptLanguage.PYTHON, first);

    assertThat(second).isNotSameAs(first);
    assertThat(third).isSameAs(second);
    assertThat(provider.ensureCalls()).isEqualTo(2);
  }

  @Test
  void failsTheAttemptsOnceClosed() {
    readiness = new ExecutorReadiness(new FakeProvider(), BACKOFF, WINDOW, now::get);
    readiness.close();

    assertThat(failureOf(readiness.start(ScriptLanguage.PYTHON)).code())
        .isEqualTo(ErrorCode.PLATFORM_INTERRUPTED);
  }

  @Test
  void failsAnAttemptWaitingForItsBackoffOnceClosed() {
    final var provider =
        new FakeProvider() {
          @Override
          public void ensureExecutors(ScriptLanguage language) {
            super.ensureExecutors(language);
            throw new ProvisioningException(ErrorCode.PLATFORM_UNAVAILABLE, "service error");
          }
        };
    readiness = new ExecutorReadiness(provider, Duration.ofMinutes(1), WINDOW, now::get);
    final var ready = readiness.start(ScriptLanguage.PYTHON);
    awaitCalls(provider, 1);
    assertThat(ready).isNotDone();

    readiness.close();

    assertThat(failureOf(ready).code()).isEqualTo(ErrorCode.PLATFORM_INTERRUPTED);
  }

  @Test
  void neverWaitsLongerThanOneMinuteBetweenAttempts() {
    for (int i = 0; i < 100; i++) {
      assertThat(ExecutorReadiness.withJitter(Duration.ofMinutes(4)))
          .isBetween(Duration.ofSeconds(30), Duration.ofMinutes(1));
      assertThat(ExecutorReadiness.withJitter(Duration.ofSeconds(10)))
          .isBetween(Duration.ofSeconds(5), Duration.ofSeconds(10));
    }
  }

  @Test
  void turnsAnUnexpectedExceptionIntoARetryableFailure() throws Exception {
    final var provider =
        new FakeProvider() {
          @Override
          public void ensureExecutors(ScriptLanguage language) {
            super.ensureExecutors(language);
            if (ensureCalls() == 1) {
              throw new IllegalStateException("unexpected");
            }
          }
        };
    readiness = new ExecutorReadiness(provider, BACKOFF, WINDOW, now::get);

    readiness.start(ScriptLanguage.PYTHON).get(5, TimeUnit.SECONDS);

    assertThat(provider.ensureCalls()).isEqualTo(2);
  }

  private static FakeProvider denying() {
    return new FakeProvider() {
      @Override
      public void ensureExecutors(ScriptLanguage language) {
        super.ensureExecutors(language);
        throw ProvisioningException.permissionDenied("AccessDenied", null);
      }
    };
  }

  /** Waits until the provider was called that often: each failure before is recorded. */
  private static void awaitCalls(FakeProvider provider, int calls) {
    Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> provider.ensureCalls() >= calls);
  }

  /** The failure the attempt completes with. */
  private static ProvisioningException failureOf(CompletableFuture<Void> ready) {
    try {
      ready.get(5, TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      assertThat(e.getCause()).isInstanceOf(ProvisioningException.class);
      return (ProvisioningException) e.getCause();
    } catch (Exception e) {
      throw new AssertionError("The attempt did not fail", e);
    }
    throw new AssertionError("The attempt succeeded");
  }
}
