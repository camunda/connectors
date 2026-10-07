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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Development provider: provisioning is simulated, execution is local. Trusted development only.
 *
 * <p>Provisioning keeps the artifact in memory under its deterministic deployment name and becomes
 * ready after a configurable delay, so that the first job of an artifact waits as it would on a
 * cloud provider. A second request for the same name adopts the existing deployment and waits only
 * for the rest of the delay. Invocation runs the script in a local Node.js or Python subprocess
 * with the Connector Runtime operating-system identity; this is not a sandbox.
 */
public final class FakeManagedCodeProvider implements ManagedCodeProvider {

  public static final String NAME = "fake";

  private record FakeDeployment(
      ArtifactSpec artifact, CompletableFuture<ProviderDeployment> ready) {}

  private final LocalProcessScriptExecutor executor;
  private final ObjectMapper objectMapper;
  private final Duration provisioningDelay;
  private final Map<String, FakeDeployment> deployments = new ConcurrentHashMap<>();

  FakeManagedCodeProvider(
      LocalProcessScriptExecutor executor, ObjectMapper objectMapper, Duration provisioningDelay) {
    this.executor = executor;
    this.objectMapper = objectMapper;
    this.provisioningDelay = provisioningDelay;
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public ProvisioningHandle ensureProvisioned(ArtifactSpec artifact) {
    if (artifact.dependencies().isPresent()) {
      throw new ProvisioningException(
          "DEPENDENCIES_UNSUPPORTED",
          "The fake managed-code provider does not support dependency manifests; remove the"
              + " 'dependencies' linked resource or use a cloud provider",
          false);
    }
    final var deployment =
        deployments.computeIfAbsent(
            artifact.deploymentName(), name -> new FakeDeployment(artifact, readyAfterDelay(name)));
    return new ProvisioningHandle(artifact.deploymentName(), deployment.ready().copy());
  }

  @Override
  public ExecutionResponse invoke(ProviderDeployment deployment, ExecutionRequest request) {
    final var fakeDeployment = deployments.get(deployment.deploymentId());
    if (fakeDeployment == null || !fakeDeployment.ready().isDone()) {
      return ExecutionResponse.failed(
          ExecutionResponse.DEPLOYMENT_MISSING,
          "Provider deployment '%s' does not exist".formatted(deployment.deploymentId()),
          false);
    }
    final var artifact = fakeDeployment.artifact();
    try {
      final var result = executor.execute(artifact.language(), artifact.script(), request);
      return objectMapper.treeToValue(result.response(), ExecutionResponse.class);
    } catch (IllegalArgumentException e) {
      return ExecutionResponse.failed("RESOURCE_LIMIT", e.getMessage(), false);
    } catch (IllegalStateException e) {
      // No usable local interpreter: the developer can install one without changing the process.
      return ExecutionResponse.failed("PLATFORM_UNAVAILABLE", e.getMessage(), true);
    } catch (IOException e) {
      return ExecutionResponse.failed(
          "PLATFORM_UNAVAILABLE", "Local execution failed: " + e.getMessage(), true);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return ExecutionResponse.failed("PLATFORM_UNAVAILABLE", "Local execution interrupted", true);
    }
  }

  /** Simulates drift: the provider resource disappears behind the registry's back. */
  void delete(String deploymentName) {
    deployments.remove(deploymentName);
  }

  private CompletableFuture<ProviderDeployment> readyAfterDelay(String deploymentName) {
    final var deployment = new ProviderDeployment(NAME, deploymentName);
    if (provisioningDelay.isZero()) {
      return CompletableFuture.completedFuture(deployment);
    }
    return CompletableFuture.supplyAsync(
        () -> deployment,
        CompletableFuture.delayedExecutor(provisioningDelay.toMillis(), TimeUnit.MILLISECONDS));
  }
}
