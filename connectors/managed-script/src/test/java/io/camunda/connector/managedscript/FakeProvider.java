/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.managedscript;

import io.camunda.connector.managedscript.api.ExecutionRequest;
import io.camunda.connector.managedscript.api.ExecutionResponse;
import io.camunda.connector.managedscript.api.ManagedCodeProvider;
import io.camunda.connector.managedscript.api.ScriptLanguage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/** A provider that answers with queued responses and records what it is asked. */
class FakeProvider implements ManagedCodeProvider {

  record Call(ScriptLanguage language, ExecutionRequest request) {}

  private final String name;
  private final Deque<ExecutionResponse> responses = new ArrayDeque<>();
  private final Deque<RuntimeException> ensureFailures = new ArrayDeque<>();
  final List<Call> calls = new ArrayList<>();
  int ensureCalls;

  FakeProvider() {
    this("fake");
  }

  FakeProvider(String name) {
    this.name = name;
  }

  FakeProvider respond(ExecutionResponse... queued) {
    responses.addAll(List.of(queued));
    return this;
  }

  FakeProvider failEnsure(RuntimeException failure) {
    ensureFailures.add(failure);
    return this;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public void ensureExecutors() {
    ensureCalls++;
    if (!ensureFailures.isEmpty()) {
      throw ensureFailures.poll();
    }
  }

  @Override
  public ExecutionResponse execute(ScriptLanguage language, ExecutionRequest request) {
    calls.add(new Call(language, request));
    return responses.isEmpty() ? ExecutionResponse.completed(Map.of()) : responses.poll();
  }
}
