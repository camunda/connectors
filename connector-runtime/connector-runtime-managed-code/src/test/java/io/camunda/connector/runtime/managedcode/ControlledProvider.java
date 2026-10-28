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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** Test provider whose executor creation and execution outcomes are controlled by the test. */
final class ControlledProvider implements ManagedCodeProvider {

  final AtomicInteger ensures = new AtomicInteger();
  final List<ScriptLanguage> languages = new CopyOnWriteArrayList<>();
  final List<ExecutionRequest> requests = new CopyOnWriteArrayList<>();
  private final Deque<ExecutionResponse> responses = new ArrayDeque<>();
  volatile CompletableFuture<Void> ready;
  volatile RuntimeException ensureFailure;

  /** {@link #ensureExecutors()} blocks until the future completes. */
  ControlledProvider readyWhen(CompletableFuture<Void> ready) {
    this.ready = ready;
    return this;
  }

  ControlledProvider failEnsure(RuntimeException failure) {
    this.ensureFailure = failure;
    return this;
  }

  synchronized ControlledProvider respond(ExecutionResponse... responses) {
    this.responses.addAll(List.of(responses));
    return this;
  }

  @Override
  public String name() {
    return "controlled";
  }

  @Override
  public void ensureExecutors() {
    ensures.incrementAndGet();
    final var failure = ensureFailure;
    if (failure != null) {
      throw failure;
    }
    final var future = ready;
    if (future != null) {
      future.join();
    }
  }

  @Override
  public synchronized ExecutionResponse execute(ScriptLanguage language, ExecutionRequest request) {
    languages.add(language);
    requests.add(request);
    final var response = responses.poll();
    return response == null ? ExecutionResponse.completed(Map.of()) : response;
  }
}
