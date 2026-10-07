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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DeploymentRegistryTest {

  private final ControlledProvider provider = new ControlledProvider();
  private final DeploymentRegistry registry =
      new DeploymentRegistry(
          provider,
          Executors.newFixedThreadPool(2),
          Clock.fixed(Instant.parse("2026-10-07T10:00:00Z"), ZoneOffset.UTC));

  @AfterEach
  void close() {
    registry.close();
  }

  @Test
  void sharesOneProvisioningAndTracksItsStatus() throws Exception {
    final var ready = new CompletableFuture<ProviderDeployment>();
    provider.readyWhen(ready);
    final var artifact = artifact("a");

    final var first = registry.ensureDeployment(artifact);
    final var second = registry.ensureDeployment(artifact);

    assertThat(second).isSameAs(first);
    awaitStatus(artifact, DeploymentRegistry.Status.DEPLOYING);
    ready.complete(null);
    final var deployment = first.get(5, TimeUnit.SECONDS);

    assertThat(deployment.deploymentId()).isEqualTo(artifact.deploymentName());
    assertThat(provider.provisionings).hasValue(1);
    assertThat(registry.find(artifact.key()))
        .hasValueSatisfying(
            entry -> {
              assertThat(entry.status()).isEqualTo(DeploymentRegistry.Status.READY);
              assertThat(entry.providerDeploymentId()).isEqualTo(artifact.deploymentName());
              assertThat(entry.language()).isEqualTo("javascript");
              assertThat(entry.createdAt()).isEqualTo(Instant.parse("2026-10-07T10:00:00Z"));
              assertThat(entry.lastUsedAt()).isNull();
            });

    registry.markUsed(artifact.key());

    assertThat(registry.find(artifact.key()).orElseThrow().lastUsedAt()).isNotNull();
  }

  @Test
  void keepsPermanentFailuresAndForgetsTransientOnes() throws Exception {
    final var permanent = artifact("permanent");
    provider.failProvisioning(new ProvisioningException("BUILD_FAILED", "syntax", false));
    awaitFailure(registry.ensureDeployment(permanent));

    assertThat(registry.find(permanent.key()))
        .hasValueSatisfying(
            entry -> {
              assertThat(entry.status()).isEqualTo(DeploymentRegistry.Status.FAILED);
              assertThat(entry.failureCode()).isEqualTo("BUILD_FAILED");
              assertThat(entry.failureMessage()).isEqualTo("syntax");
            });
    awaitFailure(registry.ensureDeployment(permanent));
    assertThat(provider.provisionings).hasValue(1);

    final var transientArtifact = artifact("transient");
    provider.failProvisioning(new IllegalStateException("connection reset"));
    final var failure = awaitFailure(registry.ensureDeployment(transientArtifact));

    assertThat(failure).isInstanceOf(ProvisioningException.class);
    assertThat(((ProvisioningException) failure).retryable()).isTrue();
    assertThat(registry.find(transientArtifact.key())).isEmpty();

    provider.failProvisioning(null);
    registry.ensureDeployment(transientArtifact).get(5, TimeUnit.SECONDS);
    assertThat(provider.provisionings).hasValue(3);
  }

  @Test
  void evictsOnlyTheMissingDeployment() throws Exception {
    final var artifact = artifact("a");
    final var deployment = registry.ensureDeployment(artifact).get(5, TimeUnit.SECONDS);

    registry.evict(artifact.key(), new ProviderDeployment("controlled", "other"));
    assertThat(registry.find(artifact.key())).isPresent();

    registry.evict(artifact.key(), deployment);
    assertThat(registry.find(artifact.key())).isEmpty();

    registry.ensureDeployment(artifact).get(5, TimeUnit.SECONDS);
    assertThat(provider.provisionings).hasValue(2);
  }

  private void awaitStatus(ArtifactSpec artifact, DeploymentRegistry.Status status)
      throws InterruptedException {
    for (int i = 0; i < 100; i++) {
      if (registry.find(artifact.key()).map(DeploymentRegistry.Entry::status).orElse(null)
          == status) {
        return;
      }
      Thread.sleep(20);
    }
    assertThat(registry.find(artifact.key()).orElseThrow().status()).isEqualTo(status);
  }

  private static Throwable awaitFailure(CompletableFuture<ProviderDeployment> future)
      throws Exception {
    try {
      future.get(5, TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      return e.getCause();
    }
    throw new AssertionError("provisioning should have failed");
  }

  private static ArtifactSpec artifact(String script) {
    return ArtifactSpec.create(
        "default",
        "tenant-a",
        "controlled",
        ScriptLanguage.JAVASCRIPT,
        "nodejs22",
        "script.js",
        script.getBytes(StandardCharsets.UTF_8),
        Optional.empty());
  }
}
