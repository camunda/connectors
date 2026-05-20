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
import java.util.concurrent.Future;

/**
 * Routes lifecycle work to a per-{@link LaneKey} serial executor. Different keys run in parallel;
 * tasks for the same key run one at a time in submission order.
 *
 * <p>Every submission returns a {@link Future} held by the underlying executor, and exceptions are
 * caught and logged at the dispatcher boundary.
 */
public interface LaneDispatcher {

  /** Submits a task to the lane for {@code key}. The task runs synchronously on the lane thread. */
  Future<?> submit(LaneKey key, Runnable task);

  /** Submits a task to the lane for {@code key} and returns its result via the future. */
  <T> Future<T> submit(LaneKey key, Callable<T> task);
}
