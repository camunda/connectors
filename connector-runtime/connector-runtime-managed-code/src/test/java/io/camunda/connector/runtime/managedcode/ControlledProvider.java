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

/** Test provider whose provisioning and invocation outcomes are controlled by the test. */
final class ControlledProvider implements ManagedCodeProvider {

  final AtomicInteger provisionings = new AtomicInteger();
  final List<ArtifactSpec> provisioned = new CopyOnWriteArrayList<>();
  final List<ExecutionRequest> requests = new CopyOnWriteArrayList<>();
  private final Deque<ExecutionResponse> responses = new ArrayDeque<>();
  volatile CompletableFuture<ProviderDeployment> ready;
  volatile RuntimeException provisioningFailure;

  ControlledProvider readyImmediately() {
    ready = null;
    return this;
  }

  ControlledProvider readyWhen(CompletableFuture<ProviderDeployment> ready) {
    this.ready = ready;
    return this;
  }

  ControlledProvider failProvisioning(RuntimeException failure) {
    this.provisioningFailure = failure;
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
  public ProvisioningHandle ensureProvisioned(ArtifactSpec artifact) {
    provisionings.incrementAndGet();
    provisioned.add(artifact);
    if (provisioningFailure != null) {
      throw provisioningFailure;
    }
    final var deployment = new ProviderDeployment(name(), artifact.deploymentName());
    final var stage =
        ready == null
            ? CompletableFuture.completedFuture(deployment)
            : ready.thenApply(ignored -> deployment);
    return new ProvisioningHandle(artifact.deploymentName(), stage);
  }

  @Override
  public synchronized ExecutionResponse invoke(
      ProviderDeployment deployment, ExecutionRequest request) {
    requests.add(request);
    final var response = responses.poll();
    return response == null ? ExecutionResponse.completed(Map.of()) : response;
  }
}
