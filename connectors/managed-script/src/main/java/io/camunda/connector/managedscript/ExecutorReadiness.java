/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.managedscript;

import io.camunda.connector.managedscript.api.ErrorCode;
import io.camunda.connector.managedscript.api.ManagedCodeProvider;
import io.camunda.connector.managedscript.api.ProvisioningException;
import io.camunda.connector.managedscript.api.ScriptLanguage;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Makes the executor of each language ready in the background, so that jobs wait for it instead of
 * provisioning it. Each language has its own attempt, on its own thread, so that a failing executor
 * does not hold back the others.
 *
 * <p>A retryable failure, such as throttling, is tried again with a jittered backoff that doubles
 * up to 1 minute, until it succeeds. A permission denial counts as retryable while the grants of a
 * new cluster propagate: within the grant-propagation window from the first attempt of this
 * runtime. A permanent failure completes the attempt exceptionally, and the first job after the
 * backoff starts a new one, so that a fix is picked up without a restart and a burst of jobs does
 * not repeat the attempt.
 */
final class ExecutorReadiness implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(ExecutorReadiness.class);
  private static final Duration MAX_BACKOFF = Duration.ofMinutes(1);

  private final ManagedCodeProvider provider;
  private final Duration retryBackoff;
  private final Duration grantPropagationWindow;
  private final LongSupplier nanoTime;
  private final ScheduledExecutorService scheduler;
  private final Map<ScriptLanguage, Attempts> languages = new EnumMap<>(ScriptLanguage.class);

  /**
   * When this runtime first tried to make an executor ready; the window is never measured again.
   */
  private Long firstAttemptNanos;

  private volatile boolean closed;

  /**
   * @param retryBackoff the first backoff of a retryable failure, and how long a permanent failure
   *     is kept before a job tries again
   */
  ExecutorReadiness(
      ManagedCodeProvider provider, Duration retryBackoff, Duration grantPropagationWindow) {
    this(provider, retryBackoff, grantPropagationWindow, System::nanoTime);
  }

  ExecutorReadiness(
      ManagedCodeProvider provider,
      Duration retryBackoff,
      Duration grantPropagationWindow,
      LongSupplier nanoTime) {
    this.provider = provider;
    this.retryBackoff = retryBackoff;
    this.grantPropagationWindow = grantPropagationWindow;
    this.nanoTime = nanoTime;
    // One thread per language: an attempt blocks while the provider creates an executor.
    final var executor =
        new ScheduledThreadPoolExecutor(
            ScriptLanguage.values().length,
            runnable -> {
              final var thread = new Thread(runnable, "managed-code-executors");
              thread.setDaemon(true);
              return thread;
            });
    executor.setRemoveOnCancelPolicy(true);
    this.scheduler = executor;
    for (var language : ScriptLanguage.values()) {
      languages.put(language, new Attempts());
    }
  }

  /** Starts making every executor ready, once the runtime is ready. */
  void startAll() {
    for (var language : ScriptLanguage.values()) {
      start(language);
    }
  }

  /**
   * Starts making the executor of the language ready, unless that is running or done. An attempt
   * that failed permanently is started again once the backoff has passed.
   *
   * @return completes once the executor can be invoked, or exceptionally with the {@link
   *     ProvisioningException} that failed the attempt
   */
  synchronized CompletableFuture<Void> start(ScriptLanguage language) {
    final var attempts = languages.get(language);
    if (attempts.ready != null
        && attempts.ready.isCompletedExceptionally()
        && nanoTime.getAsLong() - attempts.failedAtNanos >= retryBackoff.toNanos()) {
      attempts.ready = null;
    }
    if (attempts.ready == null) {
      attempts.ready = new CompletableFuture<>();
      if (firstAttemptNanos == null) {
        firstAttemptNanos = nanoTime.getAsLong();
      }
      schedule(language, attempts.ready, Duration.ZERO, retryBackoff);
    }
    return attempts.ready;
  }

  /**
   * Starts again after the executor of the language was reported missing, unless another job did
   * already: only the attempt that the job waited for is replaced, and only once it is over.
   */
  synchronized CompletableFuture<Void> restart(
      ScriptLanguage language, CompletableFuture<Void> waitedFor) {
    final var attempts = languages.get(language);
    if (attempts.ready == waitedFor && waitedFor.isDone()) {
      attempts.ready = null;
    }
    return start(language);
  }

  /**
   * The last failure of the language, when making its executor ready has kept failing for longer
   * than the given duration; empty otherwise, and during a permission denial within the window.
   * Lets waiting jobs consume their retries when a retryable failure lasts, instead of waiting
   * again on every attempt without ever raising an incident.
   */
  synchronized Optional<ProvisioningException> lastingFailure(
      ScriptLanguage language, Duration longerThan) {
    final var attempts = languages.get(language);
    final var failure = attempts.lastFailure;
    if (failure == null
        || nanoTime.getAsLong() - attempts.failingSinceNanos <= longerThan.toNanos()
        || failure.permissionDenied() && withinGrantPropagationWindow()) {
      return Optional.empty();
    }
    return Optional.of(failure);
  }

  /** Completes the attempts that are still running, so that no job waits for them. */
  @Override
  public void close() {
    closed = true;
    scheduler.shutdownNow();
    synchronized (this) {
      for (var attempts : languages.values()) {
        if (attempts.ready != null) {
          attempts.ready.completeExceptionally(stopped());
        }
      }
    }
  }

  private void schedule(
      ScriptLanguage language, CompletableFuture<Void> ready, Duration delay, Duration backoff) {
    try {
      scheduler.schedule(
          () -> attempt(language, ready, backoff), delay.toNanos(), TimeUnit.NANOSECONDS);
    } catch (RejectedExecutionException e) {
      ready.completeExceptionally(stopped());
    }
  }

  private void attempt(ScriptLanguage language, CompletableFuture<Void> ready, Duration backoff) {
    if (closed) {
      ready.completeExceptionally(stopped());
      return;
    }
    ProvisioningException failure;
    try {
      provider.ensureExecutors(language);
      succeeded(language, ready);
      return;
    } catch (ProvisioningException e) {
      failure = e;
    } catch (RuntimeException e) {
      // Only the type: the message of an unexpected exception may quote anything.
      failure =
          new ProvisioningException(
              ErrorCode.PLATFORM_UNAVAILABLE,
              "Making the executor ready failed: " + e.getClass().getName(),
              e);
    }
    if (!isRetryable(language, failure)) {
      LOG.warn(
          "The managed-code executor of {} cannot be made ready by provider '{}' ({}): {}",
          language.language(),
          provider.name(),
          failure.code(),
          failure.getMessage());
      failed(language, ready, failure);
      return;
    }
    final var delay = withJitter(backoff);
    LOG.info(
        "The managed-code executor of {} is not ready ({}): {}; trying again in {}",
        language.language(),
        failure.code(),
        failure.getMessage(),
        delay);
    final var doubled = backoff.multipliedBy(2);
    schedule(language, ready, delay, doubled.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : doubled);
  }

  /** Records the failure, and whether to try again. */
  private synchronized boolean isRetryable(ScriptLanguage language, ProvisioningException failure) {
    final var attempts = languages.get(language);
    if (attempts.lastFailure == null) {
      attempts.failingSinceNanos = nanoTime.getAsLong();
    }
    attempts.lastFailure = failure;
    return failure.code().retryable()
        || failure.permissionDenied() && withinGrantPropagationWindow();
  }

  private synchronized void succeeded(ScriptLanguage language, CompletableFuture<Void> ready) {
    final var attempts = languages.get(language);
    attempts.lastFailure = null;
    LOG.info(
        "The managed-code executor of {} is ready on provider '{}'",
        language.language(),
        provider.name());
    ready.complete(null);
  }

  private synchronized void failed(
      ScriptLanguage language, CompletableFuture<Void> ready, ProvisioningException failure) {
    final var attempts = languages.get(language);
    attempts.failedAtNanos = nanoTime.getAsLong();
    // The next attempt is a new episode: a lasting failure is measured from its start.
    attempts.lastFailure = null;
    ready.completeExceptionally(failure);
  }

  /**
   * Whether a permission denial may still come from grants that propagate: within the window from
   * the first attempt of this runtime, which no later attempt renews.
   */
  synchronized boolean withinGrantPropagationWindow() {
    return firstAttemptNanos != null
        && nanoTime.getAsLong() - firstAttemptNanos < grantPropagationWindow.toNanos();
  }

  private static ProvisioningException stopped() {
    return new ProvisioningException(
        ErrorCode.PLATFORM_INTERRUPTED, "The managed-script connector is stopping");
  }

  /** Up to 50% more, so that runtimes started together do not try again in step. */
  private static Duration withJitter(Duration duration) {
    final long millis = Math.max(1, duration.toMillis());
    return Duration.ofMillis(millis + ThreadLocalRandom.current().nextLong(millis / 2 + 1));
  }

  /** The current attempt of a language and its failures. */
  private static final class Attempts {
    private CompletableFuture<Void> ready;
    private long failedAtNanos;
    private long failingSinceNanos;
    private ProvisioningException lastFailure;
  }
}
