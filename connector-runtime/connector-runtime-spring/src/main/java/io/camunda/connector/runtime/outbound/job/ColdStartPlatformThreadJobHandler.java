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

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.api.worker.JobHandler;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.MDC;

/**
 * Handles the jobs of one connector type on a platform thread until the first of them has finished,
 * and on the calling thread after that.
 *
 * <p>On JDK 24+, the runtime can deadlock when job handlers run on virtual threads
 * (camunda/connectors#8440, #10102). The runtime is a Spring Boot nested jar, so class loading and
 * resource reads go through one {@code UrlNestedJarFile} monitor. A virtual thread that reads a
 * resource from the jar holds that monitor, and is unmounted from its carrier if it has to wait for
 * another lock during the read. Virtual threads that load a class for the first time wait for the
 * same monitor, but stay pinned to their carriers while they wait. Once every carrier is pinned,
 * the unmounted owner cannot run again, and the runtime stops.
 *
 * <p>This happens while a connector type is cold: its first jobs load most of its classes and read
 * most of its resources, and several of them usually arrive at once. A platform thread is never
 * unmounted, so a cold job that runs on one always gets to release the monitor. The calling virtual
 * thread only waits for the result and does not touch the jar.
 *
 * <p>The first job of the type to finish, whether it succeeded or failed, marks the type as warm.
 * Jobs that start after that run directly on the calling thread. Jobs that are not called on a
 * virtual thread (virtual threads disabled) always run directly.
 *
 * <p>This lowers the odds of the deadlock a lot, but does not rule it out: a warm connector can
 * still take a code path it has not taken before.
 */
public class ColdStartPlatformThreadJobHandler implements JobHandler {

  private final JobHandler delegate;
  private final Executor platformExecutor;
  private final AtomicBoolean warm = new AtomicBoolean(false);

  /**
   * @param delegate the job handler to run
   * @param platformExecutor runs the cold jobs; it must run them on platform threads and must not
   *     queue them behind each other, since several cold jobs can arrive at once
   */
  public ColdStartPlatformThreadJobHandler(JobHandler delegate, Executor platformExecutor) {
    this.delegate = delegate;
    this.platformExecutor = platformExecutor;
  }

  @Override
  public void handle(JobClient client, ActivatedJob job) throws Exception {
    if (warm.get() || !Thread.currentThread().isVirtual()) {
      delegate.handle(client, job);
      return;
    }
    try {
      handleOnPlatformThread(client, job);
    } finally {
      warm.set(true);
    }
  }

  boolean isWarm() {
    return warm.get();
  }

  private void handleOnPlatformThread(JobClient client, ActivatedJob job) throws Exception {
    var task =
        new PlatformTask(
            client, job, MDC.getCopyOfContextMap(), Thread.currentThread().getContextClassLoader());
    try {
      platformExecutor.execute(task);
    } catch (RejectedExecutionException e) {
      // the executor is shut down (runtime shutdown): handle the job here instead of dropping it
      delegate.handle(client, job);
      return;
    }
    task.awaitOutcome();
  }

  /**
   * Runs the delegate on a platform thread with the caller's MDC and context class loader, and lets
   * the waiting thread forward interrupts.
   */
  private final class PlatformTask implements Runnable {

    private final JobClient client;
    private final ActivatedJob job;
    private final Map<String, String> mdc;
    private final ClassLoader contextClassLoader;
    private final CompletableFuture<Void> outcome = new CompletableFuture<>();
    private Thread runner;
    private boolean interruptRequested;

    private PlatformTask(
        JobClient client,
        ActivatedJob job,
        Map<String, String> mdc,
        ClassLoader contextClassLoader) {
      this.client = client;
      this.job = job;
      this.mdc = mdc;
      this.contextClassLoader = contextClassLoader;
    }

    @Override
    public void run() {
      synchronized (this) {
        runner = Thread.currentThread();
        if (interruptRequested) {
          runner.interrupt();
        }
      }
      var thread = Thread.currentThread();
      var previousContextClassLoader = thread.getContextClassLoader();
      thread.setContextClassLoader(contextClassLoader);
      setMdc(mdc);
      try {
        delegate.handle(client, job);
        outcome.complete(null);
      } catch (Throwable t) {
        outcome.completeExceptionally(t);
      } finally {
        MDC.clear();
        thread.setContextClassLoader(previousContextClassLoader);
        synchronized (this) {
          runner = null;
          // the pool thread is reused, so an interrupt meant for this job must not leak
          Thread.interrupted();
        }
      }
    }

    private synchronized void interruptRunner() {
      interruptRequested = true;
      if (runner != null) {
        runner.interrupt();
      }
    }

    /**
     * Waits until the delegate has finished, even if the waiting thread is interrupted: the
     * delegate may still complete or fail the job, so returning early would report a second outcome
     * for it. An interrupt is forwarded to the platform thread and restored afterwards.
     */
    private void awaitOutcome() throws Exception {
      boolean interrupted = false;
      try {
        while (true) {
          try {
            outcome.get();
            return;
          } catch (InterruptedException e) {
            interrupted = true;
            interruptRunner();
          } catch (ExecutionException e) {
            throw rethrow(e.getCause());
          }
        }
      } finally {
        if (interrupted) {
          Thread.currentThread().interrupt();
        }
      }
    }
  }

  private static void setMdc(Map<String, String> mdc) {
    if (mdc == null) {
      MDC.clear();
    } else {
      MDC.setContextMap(mdc);
    }
  }

  private static Exception rethrow(Throwable cause) {
    if (cause instanceof Exception exception) {
      return exception;
    }
    if (cause instanceof Error error) {
      throw error;
    }
    return new RuntimeException(cause);
  }
}
