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
package io.camunda.connector.runtime.managedcode;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates the executors of the cluster once, in the background, when the runtime starts, so that no
 * job provisions anything. Jobs wait on the shared future for a bounded time. A retryable failure,
 * such as throttling or a grant not yet in effect, is retried with a growing backoff until it
 * succeeds; a permanent one, such as a missing permission, completes the future exceptionally, and
 * the first {@link #start()} after the backoff tries again, so that a fixed permission is picked up
 * without a restart and a burst of jobs does not repeat the attempt.
 */
final class ExecutorReadiness implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(ExecutorReadiness.class);
  private static final Duration MAX_BACKOFF = Duration.ofMinutes(1);

  private final ManagedCodeProvider provider;
  private final Duration retryBackoff;
  private final ExecutorService thread =
      Executors.newSingleThreadExecutor(
          runnable -> {
            final var worker = new Thread(runnable, "managed-code-executors");
            worker.setDaemon(true);
            return worker;
          });
  private CompletableFuture<Void> ready;
  private long failedAtNanos;
  private volatile boolean closed;

  ExecutorReadiness(ManagedCodeProvider provider, Duration retryBackoff) {
    this.provider = provider;
    this.retryBackoff = retryBackoff;
  }

  /**
   * Starts creating the executors, unless that is already running or done. An attempt that failed
   * permanently is started again once the backoff has passed.
   */
  synchronized CompletableFuture<Void> start() {
    if (ready != null
        && ready.isCompletedExceptionally()
        && System.nanoTime() - failedAtNanos >= retryBackoff.toNanos()) {
      ready = null;
    }
    if (ready == null) {
      ready = new CompletableFuture<>();
      final var future = ready;
      thread.execute(() -> ensure(future));
    }
    return ready;
  }

  /**
   * Starts again once the current attempt is over, for example after an executor was reported
   * missing or a permanent failure was fixed. An attempt still running is kept.
   */
  synchronized CompletableFuture<Void> restart() {
    if (ready != null && ready.isDone()) {
      ready = null;
    }
    return start();
  }

  @Override
  public void close() {
    closed = true;
    thread.shutdownNow();
  }

  private void ensure(CompletableFuture<Void> future) {
    var backoff = retryBackoff;
    while (!closed) {
      try {
        provider.ensureExecutors();
        LOG.info("Managed-code executors of provider '{}' are ready", provider.name());
        future.complete(null);
        return;
      } catch (ProvisioningException e) {
        if (!e.retryable()) {
          LOG.warn(
              "Managed-code executors of provider '{}' cannot be created ({}): {}",
              provider.name(),
              e.code(),
              e.getMessage());
          failed(future, e);
          return;
        }
        LOG.info(
            "Managed-code executors of provider '{}' are not ready ({}); retrying in {}",
            provider.name(),
            e.code(),
            backoff);
      } catch (RuntimeException e) {
        LOG.warn("Creating the managed-code executors failed; retrying in {}", backoff, e);
      }
      try {
        Thread.sleep(withJitter(backoff));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
      backoff =
          backoff.multipliedBy(2).compareTo(MAX_BACKOFF) > 0
              ? MAX_BACKOFF
              : backoff.multipliedBy(2);
    }
    failed(
        future,
        new ProvisioningException(
            "PLATFORM_UNAVAILABLE", "The managed-code executors were not created", true));
  }

  private synchronized void failed(CompletableFuture<Void> future, ProvisioningException failure) {
    failedAtNanos = System.nanoTime();
    future.completeExceptionally(failure);
  }

  /** Up to 50% more, so that runtimes started together do not retry in step. */
  private static Duration withJitter(Duration duration) {
    final long millis = Math.max(1, duration.toMillis());
    return Duration.ofMillis(millis + ThreadLocalRandom.current().nextLong(millis / 2 + 1));
  }
}
