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

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Striped serial executor backed by virtual threads. A fixed number of lanes is allocated at
 * construction; each {@link ProcessKey} is routed to a lane via {@code hash(key) % laneCount}.
 *
 * <p>Trade-offs vs. a per-key map of executors:
 *
 * <ul>
 *   <li><b>Bounded memory</b>: lane count is fixed (default 128, ~128 KB total), independent of how
 *       many distinct processes the runtime has ever seen.
 *   <li><b>No eviction</b>: lanes are created once at startup and live for the JVM lifetime. No
 *       race conditions, no eviction bookkeeping.
 *   <li><b>Per-key serialization preserved</b>: a given {@link ProcessKey} always maps to the same
 *       lane and runs FIFO on that lane.
 *   <li><b>Occasional unrelated-process serialization</b>: two distinct keys whose hashes collide
 *       share a lane and serialize through it. With N=128 lanes and 1000 active processes, average
 *       lane queue depth is ~8; lifecycle work is millisecond-scale, so the queueing is invisible
 *       in practice.
 * </ul>
 *
 * <p>Concurrency invariants:
 *
 * <ul>
 *   <li>For a given {@link ProcessKey}, tasks run sequentially in submission order.
 *   <li>Tasks for keys hashing to distinct lanes run in parallel.
 *   <li>Every task is wrapped in an outer try/catch — exceptions are logged here, never silently
 *       swallowed, and never propagated to the caller via a future they may forget to await.
 * </ul>
 *
 * <p>The JVM reclaims everything on process exit, so no explicit shutdown is required.
 */
public class VirtualThreadLaneDispatcher implements LaneDispatcher {

  /** Default lane count if no explicit value is supplied. */
  public static final int DEFAULT_LANE_COUNT = 128;

  private static final Logger LOG = LoggerFactory.getLogger(VirtualThreadLaneDispatcher.class);

  private final ExecutorService[] lanes;

  public VirtualThreadLaneDispatcher() {
    this(DEFAULT_LANE_COUNT);
  }

  public VirtualThreadLaneDispatcher(int laneCount) {
    if (laneCount < 1) {
      throw new IllegalArgumentException("laneCount must be >= 1, got " + laneCount);
    }
    this.lanes = new ExecutorService[laneCount];
    for (int i = 0; i < laneCount; i++) {
      this.lanes[i] =
          Executors.newSingleThreadExecutor(Thread.ofVirtual().name("inbound-lane-", i).factory());
    }
  }

  @Override
  public Future<?> submit(ProcessKey key, Runnable task) {
    return laneFor(key).submit(wrap(key, task));
  }

  @Override
  public <T> Future<T> submit(ProcessKey key, Callable<T> task) {
    return laneFor(key).submit(wrap(key, task));
  }

  /** Visible for tests: returns the lane index that the given key routes to. */
  int laneIndexFor(ProcessKey key) {
    return Math.floorMod(key.hashCode(), lanes.length);
  }

  private ExecutorService laneFor(ProcessKey key) {
    int index = laneIndexFor(key);
    LOG.debug(
        "Routing task for {} to lane {} (hash {}, lane count {})",
        key,
        index,
        key.hashCode(),
        lanes.length);
    return lanes[laneIndexFor(key)];
  }

  private Runnable wrap(ProcessKey key, Runnable task) {
    return () -> {
      try {
        task.run();
      } catch (Throwable t) {
        handleTaskFailure(key, t);
      }
    };
  }

  private <T> Callable<T> wrap(ProcessKey key, Callable<T> task) {
    return () -> {
      try {
        return task.call();
      } catch (Throwable t) {
        handleTaskFailure(key, t);
        if (t instanceof Exception e) {
          throw e;
        }
        throw new RuntimeException(t);
      }
    };
  }

  private void handleTaskFailure(ProcessKey key, Throwable t) {
    if (t instanceof InterruptedException) {
      Thread.currentThread().interrupt();
      LOG.debug("Lane task for {} interrupted", key);
      return;
    }
    LOG.error("Lifecycle task failed for process {}", key, t);
  }
}
