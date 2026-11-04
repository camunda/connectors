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

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.api.worker.JobHandler;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class ColdStartPlatformThreadJobHandlerTest {

  private static final long TIMEOUT_SECONDS = 10;

  private final JobClient client = mock(JobClient.class);
  private final ActivatedJob job = mock(ActivatedJob.class);

  private ExecutorService executor;

  @BeforeEach
  void setUp() {
    executor =
        Executors.newCachedThreadPool(
            Thread.ofPlatform().name("test-cold-start-", 0).daemon(true).factory());
    MDC.clear();
  }

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
    MDC.clear();
  }

  /** Runs the task on a new virtual thread and returns a future of its outcome. */
  private static CompletableFuture<Void> onVirtualThread(ThrowingRunnable task) {
    var result = new CompletableFuture<Void>();
    Thread.ofVirtual()
        .start(() -> {
          try {
            task.run();
            result.complete(null);
          } catch (Throwable t) {
            result.completeExceptionally(t);
          }
        });
    return result;
  }

  private static void await(CompletableFuture<?> future) throws Exception {
    future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
  }

  private static void await(CountDownLatch latch) throws InterruptedException {
    assertThat(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).as("latch released").isTrue();
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  @Test
  void coldJobFromVirtualThread_runsOnPlatformThreadOfExecutor_andCallerWaits() throws Exception {
    var delegateThread = new AtomicReference<Thread>();
    var delegateFinished = new AtomicReference<Boolean>(false);
    var release = new CountDownLatch(1);
    var started = new CountDownLatch(1);
    JobHandler delegate =
        (c, j) -> {
          delegateThread.set(Thread.currentThread());
          started.countDown();
          await(release);
          delegateFinished.set(true);
        };
    var handler = new ColdStartPlatformThreadJobHandler(delegate, executor);

    var callerFinishedAfterDelegate = new AtomicReference<Boolean>();
    var caller =
        onVirtualThread(() -> {
          handler.handle(client, job);
          callerFinishedAfterDelegate.set(delegateFinished.get());
        });

    await(started);
    assertThat(caller).isNotDone();
    assertThat(delegateThread.get().isVirtual()).isFalse();
    assertThat(delegateThread.get().getName()).startsWith("test-cold-start-");
    release.countDown();
    await(caller);

    assertThat(callerFinishedAfterDelegate.get()).isTrue();
  }

  @Test
  void jobAfterFirstFinished_runsOnCallingVirtualThread() throws Exception {
    var threads = new ArrayList<Thread>();
    var callers = new ArrayList<Thread>();
    JobHandler delegate =
        (c, j) -> {
          synchronized (threads) {
            threads.add(Thread.currentThread());
          }
        };
    var handler = new ColdStartPlatformThreadJobHandler(delegate, executor);

    await(
        onVirtualThread(() -> {
          callers.add(Thread.currentThread());
          handler.handle(client, job);
        }));
    assertThat(handler.isWarm()).isTrue();
    await(
        onVirtualThread(() -> {
          callers.add(Thread.currentThread());
          handler.handle(client, job);
        }));

    assertThat(threads).hasSize(2);
    assertThat(threads.get(0).isVirtual()).isFalse();
    assertThat(threads.get(1)).isSameAs(callers.get(1));
    assertThat(threads.get(1).isVirtual()).isTrue();
  }

  @Test
  void firstJobThrowsUncheckedException_isRethrownAndTypeBecomesWarm() throws Exception {
    var failure = new IllegalStateException("boom");
    var handler = new ColdStartPlatformThreadJobHandler((c, j) -> throwIt(failure), executor);

    var caller = onVirtualThread(() -> handler.handle(client, job));

    assertThat(caller)
        .failsWithin(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .withThrowableOfType(java.util.concurrent.ExecutionException.class)
        .satisfies(e -> assertThat(e.getCause()).isSameAs(failure));
    assertThat(handler.isWarm()).isTrue();
  }

  @Test
  void firstJobThrowsCheckedException_isRethrownAsSameInstanceAndTypeBecomesWarm()
      throws Exception {
    var failure = new IOException("checked");
    var handler = new ColdStartPlatformThreadJobHandler((c, j) -> throwIt(failure), executor);

    var caller = onVirtualThread(() -> handler.handle(client, job));

    assertThat(caller)
        .failsWithin(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .withThrowableOfType(java.util.concurrent.ExecutionException.class)
        .satisfies(e -> assertThat(e.getCause()).isSameAs(failure));
    assertThat(handler.isWarm()).isTrue();
  }

  @Test
  void firstJobThrowsError_isRethrownAsSameInstance() {
    var failure = new AssertionError("error");
    var handler = new ColdStartPlatformThreadJobHandler((c, j) -> throwIt(failure), executor);

    var caller = onVirtualThread(() -> handler.handle(client, job));

    assertThat(caller)
        .failsWithin(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .withThrowableOfType(java.util.concurrent.ExecutionException.class)
        .satisfies(e -> assertThat(e.getCause()).isSameAs(failure));
  }

  @SuppressWarnings("unchecked")
  private static <T extends Throwable> void throwIt(Throwable t) throws T {
    throw (T) t;
  }

  @Test
  void callFromPlatformThread_runsInlineAndDoesNotMarkWarm() throws Exception {
    var delegateThread = new AtomicReference<Thread>();
    var handler =
        new ColdStartPlatformThreadJobHandler(
            (c, j) -> delegateThread.set(Thread.currentThread()), executor);

    handler.handle(client, job);

    assertThat(delegateThread.get()).isSameAs(Thread.currentThread());
    // jobs on platform threads are never routed through the executor and do not warm the type: the
    // first job on a virtual thread is still treated as cold
    assertThat(handler.isWarm()).isFalse();

    await(onVirtualThread(() -> handler.handle(client, job)));
    assertThat(delegateThread.get()).isNotSameAs(Thread.currentThread());
    assertThat(delegateThread.get().isVirtual()).isFalse();
    assertThat(handler.isWarm()).isTrue();
  }

  @Test
  void concurrentColdCalls_allRunOnPlatformThreadsConcurrently() throws Exception {
    int callers = 8;
    var allRunning = new CountDownLatch(callers);
    var threads = java.util.Collections.synchronizedList(new ArrayList<Thread>());
    JobHandler delegate =
        (c, j) -> {
          threads.add(Thread.currentThread());
          allRunning.countDown();
          // only completes if all the jobs overlap, i.e. none of them is queued behind another
          await(allRunning);
        };
    var handler = new ColdStartPlatformThreadJobHandler(delegate, executor);

    List<CompletableFuture<Void>> futures = new ArrayList<>();
    for (int i = 0; i < callers; i++) {
      futures.add(onVirtualThread(() -> handler.handle(client, job)));
    }
    for (var f : futures) {
      await(f);
    }

    assertThat(threads).hasSize(callers).allMatch(t -> !t.isVirtual());
    assertThat(threads).doesNotHaveDuplicates();
  }

  @Test
  void callerMdcIsVisibleInDelegate_andPoolThreadMdcIsClearedAfterwards() throws Exception {
    var seenInDelegate = new AtomicReference<String>();
    var poolThread = new AtomicReference<Thread>();
    var handler =
        new ColdStartPlatformThreadJobHandler(
            (c, j) -> {
              seenInDelegate.set(MDC.get("jobKey"));
              poolThread.set(Thread.currentThread());
            },
            executor);

    await(
        onVirtualThread(() -> {
          MDC.put("jobKey", "42");
          handler.handle(client, job);
        }));

    assertThat(seenInDelegate.get()).isEqualTo("42");

    var mdcOnPoolThread = new CompletableFuture<String>();
    executor.execute(() -> mdcOnPoolThread.complete(MDC.get("jobKey")));
    assertThat(mdcOnPoolThread.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isNull();
  }

  @Test
  void callerContextClassLoaderIsUsedInDelegate_andPoolThreadLoaderIsRestoredAfterwards()
      throws Exception {
    // like the production pool: threads that do not inherit thread locals start with the system
    // class loader as context class loader, which cannot see the nested application jars
    var isolatedPool =
        Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon(true).inheritInheritableThreadLocals(false).factory());
    try (var applicationLoader =
        new URLClassLoader(
            new URL[0], ColdStartPlatformThreadJobHandlerTest.class.getClassLoader())) {
      var seenInDelegate = new AtomicReference<ClassLoader>();
      var handler =
          new ColdStartPlatformThreadJobHandler(
              (c, j) -> seenInDelegate.set(Thread.currentThread().getContextClassLoader()),
              isolatedPool);

      await(
          onVirtualThread(() -> {
            Thread.currentThread().setContextClassLoader(applicationLoader);
            handler.handle(client, job);
          }));

      assertThat(seenInDelegate.get()).isSameAs(applicationLoader);
      var loaderOnPoolThread = new CompletableFuture<ClassLoader>();
      isolatedPool.execute(
          () -> loaderOnPoolThread.complete(Thread.currentThread().getContextClassLoader()));
      assertThat(loaderOnPoolThread.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
          .isSameAs(ClassLoader.getSystemClassLoader());
    } finally {
      isolatedPool.shutdownNow();
    }
  }

  @Test
  void interruptOfWaitingCaller_isForwardedAndCallerWaitsForDelegateOutcome() throws Exception {
    var delegateInterrupted = new CountDownLatch(1);
    var started = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var delegateCompleted = new AtomicReference<Boolean>(false);
    JobHandler delegate =
        (c, j) -> {
          started.countDown();
          try {
            new CountDownLatch(1).await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            // swallow the interrupt, finish the job anyway
            delegateInterrupted.countDown();
          }
          await(release);
          delegateCompleted.set(true);
        };
    var handler = new ColdStartPlatformThreadJobHandler(delegate, executor);

    var callerThread = new AtomicReference<Thread>();
    var delegateCompletedWhenCallerReturned = new AtomicReference<Boolean>();
    var callerInterruptFlag = new AtomicReference<Boolean>();
    var caller =
        onVirtualThread(() -> {
          callerThread.set(Thread.currentThread());
          handler.handle(client, job);
          delegateCompletedWhenCallerReturned.set(delegateCompleted.get());
          callerInterruptFlag.set(Thread.currentThread().isInterrupted());
        });

    await(started);
    callerThread.get().interrupt();
    await(delegateInterrupted);

    // the interrupt reached the delegate, but the caller must not return before it has finished
    assertThat(caller).isNotDone();
    assertThat(handler.isWarm()).isFalse();
    release.countDown();
    await(caller);

    // swallowed interrupt + normal completion -> caller returns normally with its flag restored
    assertThat(delegateCompletedWhenCallerReturned.get()).isTrue();
    assertThat(callerInterruptFlag.get()).isTrue();
    assertThat(handler.isWarm()).isTrue();
  }

  @Test
  void interruptBeforeDelegateIsRunning_isStillForwarded() throws Exception {
    // single pool thread, busy with another task, so the cold job is queued when the caller is
    // interrupted
    var singleThread =
        Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon(true).factory());
    try {
      var blockerRunning = new CountDownLatch(1);
      var releaseBlocker = new CountDownLatch(1);
      singleThread.execute(() -> {
        blockerRunning.countDown();
        try {
          releaseBlocker.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
          // not expected
        }
      });
      await(blockerRunning);

      var interruptSeenByDelegate = new AtomicReference<Boolean>();
      var handler =
          new ColdStartPlatformThreadJobHandler(
              (c, j) -> interruptSeenByDelegate.set(Thread.currentThread().isInterrupted()),
              singleThread);
      var callerThread = new AtomicReference<Thread>();
      var callerStarted = new CountDownLatch(1);
      var caller =
          onVirtualThread(() -> {
            callerThread.set(Thread.currentThread());
            callerStarted.countDown();
            // interrupted before handle(): the first wait fails at once and records the interrupt
            // for the queued task, the caller then parks waiting for the outcome
            Thread.currentThread().interrupt();
            handler.handle(client, job);
          });
      await(callerStarted);
      // once the caller parks, the interrupt request is recorded, so releasing the blocker is safe
      var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
      while (callerThread.get().getState() != Thread.State.WAITING
          && System.nanoTime() < deadline) {
        Thread.onSpinWait();
      }
      assertThat(callerThread.get().getState()).isEqualTo(Thread.State.WAITING);
      releaseBlocker.countDown();
      await(caller);

      assertThat(interruptSeenByDelegate.get()).isTrue();
    } finally {
      singleThread.shutdownNow();
    }
  }

  @Test
  void interruptFlagOfPoolThread_doesNotLeakIntoNextTask() throws Exception {
    var singleThread =
        Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon(true).factory());
    try {
      var firstHandler =
          new ColdStartPlatformThreadJobHandler(
              (c, j) -> Thread.currentThread().interrupt(), singleThread);
      await(onVirtualThread(() -> firstHandler.handle(client, job)));

      var interruptedInNext = new AtomicReference<Boolean>();
      var secondHandler =
          new ColdStartPlatformThreadJobHandler(
              (c, j) -> interruptedInNext.set(Thread.currentThread().isInterrupted()),
              singleThread);
      await(onVirtualThread(() -> secondHandler.handle(client, job)));

      assertThat(interruptedInNext.get()).isFalse();
    } finally {
      singleThread.shutdownNow();
    }
  }

  @Test
  void rejectingExecutor_runsJobInlineOnCallingThread() throws Exception {
    executor.shutdown();
    var delegateThread = new AtomicReference<Thread>();
    var handler =
        new ColdStartPlatformThreadJobHandler(
            (c, j) -> delegateThread.set(Thread.currentThread()), executor);
    var callerThread = new AtomicReference<Thread>();

    await(
        onVirtualThread(() -> {
          callerThread.set(Thread.currentThread());
          handler.handle(client, job);
        }));

    assertThat(delegateThread.get()).isSameAs(callerThread.get());
    assertThat(handler.isWarm()).isTrue();
  }
}
