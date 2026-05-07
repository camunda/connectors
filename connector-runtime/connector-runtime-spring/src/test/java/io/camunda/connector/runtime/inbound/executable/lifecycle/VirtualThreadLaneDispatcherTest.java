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
package io.camunda.connector.runtime.inbound.executable.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VirtualThreadLaneDispatcherTest {

  private VirtualThreadLaneDispatcher dispatcher;

  @BeforeEach
  void setUp() {
    dispatcher = new VirtualThreadLaneDispatcher();
  }

  @Test
  void tasksForSameKeyRunFifoSerially() throws Exception {
    var key = new ProcessKey("tenant", "proc");
    var observed = new ArrayList<Integer>();
    int N = 100;

    Future<?> last = null;
    for (int i = 0; i < N; i++) {
      final int seq = i;
      last =
          dispatcher.submit(
              key,
              () -> {
                observed.add(seq);
              });
    }
    last.get(5, TimeUnit.SECONDS);

    assertThat(observed).hasSize(N);
    for (int i = 0; i < N; i++) {
      assertThat(observed.get(i)).isEqualTo(i);
    }
  }

  @Test
  void tasksForDifferentKeysRunInParallel() throws Exception {
    // Striped lanes: keys must map to distinct lane indices for the parallelism check to be
    // meaningful. Generate keys deterministically until we have one per lane.
    int n = 8;
    var keys = keysOnDistinctLanes(dispatcher, n);
    var startLatch = new CountDownLatch(n);
    var releaseLatch = new CountDownLatch(1);
    var doneLatch = new CountDownLatch(n);

    for (var key : keys) {
      dispatcher.submit(
          key,
          () -> {
            try {
              startLatch.countDown();
              releaseLatch.await();
              doneLatch.countDown();
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
          });
    }

    assertThat(startLatch.await(2, TimeUnit.SECONDS))
        .as("all lanes should have a task in flight concurrently")
        .isTrue();

    releaseLatch.countDown();
    assertThat(doneLatch.await(2, TimeUnit.SECONDS)).isTrue();
  }

  /**
   * Generates {@code n} keys whose hashes route to distinct lanes on the given dispatcher. With the
   * default lane count of 128 and small {@code n}, this terminates quickly.
   */
  private static java.util.List<ProcessKey> keysOnDistinctLanes(
      VirtualThreadLaneDispatcher dispatcher, int n) {
    var byLane = new java.util.HashMap<Integer, ProcessKey>();
    int i = 0;
    while (byLane.size() < n) {
      var key = new ProcessKey("tenant", "proc-" + i++);
      byLane.putIfAbsent(dispatcher.laneIndexFor(key), key);
    }
    return new ArrayList<>(byLane.values());
  }

  @Test
  void exceptionInTaskDoesNotKillTheLane() throws Exception {
    var key = new ProcessKey("tenant", "proc");
    var counter = new AtomicInteger();

    dispatcher.submit(
        key,
        () -> {
          throw new RuntimeException("boom");
        });
    Future<?> after = dispatcher.submit(key, counter::incrementAndGet);

    after.get(2, TimeUnit.SECONDS);
    assertThat(counter.get()).isEqualTo(1);
  }

  @Test
  void callableSubmissionReturnsResult() throws Exception {
    var key = new ProcessKey("tenant", "proc");
    Future<Integer> result = dispatcher.submit(key, () -> 42);
    assertThat(result.get(2, TimeUnit.SECONDS)).isEqualTo(42);
  }

  @Test
  void callableSubmissionPropagatesException() {
    var key = new ProcessKey("tenant", "proc");
    Future<Integer> result =
        dispatcher.submit(
            key,
            () -> {
              throw new IllegalStateException("nope");
            });

    try {
      result.get(2, TimeUnit.SECONDS);
    } catch (ExecutionException ee) {
      assertThat(ee.getCause()).isInstanceOf(IllegalStateException.class);
    } catch (Exception e) {
      assertThat(false).as("expected ExecutionException, got: " + e).isTrue();
    }
  }

  @Test
  void stressManyKeysAndTasks() throws Exception {
    int keyCount = 64;
    int tasksPerKey = 200;
    var counters = new ArrayList<AtomicInteger>();
    var lastFutures = new ArrayList<Future<?>>();

    for (int i = 0; i < keyCount; i++) {
      counters.add(new AtomicInteger());
    }

    for (int i = 0; i < keyCount; i++) {
      var key = new ProcessKey("tenant", "proc-" + i);
      var counter = counters.get(i);
      Future<?> last = null;
      for (int j = 0; j < tasksPerKey; j++) {
        last = dispatcher.submit(key, counter::incrementAndGet);
      }
      lastFutures.add(last);
    }

    for (Future<?> f : lastFutures) {
      f.get(10, TimeUnit.SECONDS);
    }

    for (AtomicInteger c : counters) {
      assertThat(c.get()).isEqualTo(tasksPerKey);
    }
  }
}
