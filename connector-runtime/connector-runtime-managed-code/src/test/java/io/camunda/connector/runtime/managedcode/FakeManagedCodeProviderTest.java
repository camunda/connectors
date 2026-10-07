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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FakeManagedCodeProviderTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final LocalInterpreterDiscovery.DiscoveryReport INTERPRETERS =
      LocalInterpreterDiscovery.discover();
  private static final String SCRIPT =
      """
      export async function execute(variables) {
        return { sum: variables.a + variables.b };
      }
      """;

  @TempDir Path temporaryRoot;

  @Test
  void simulatesProvisioningDelayAndAdoptsAnExistingDeployment() throws Exception {
    final var provider = provider(Duration.ofMillis(300));
    final var artifact = artifact(Optional.empty());

    final var first = provider.ensureProvisioned(artifact);
    final var second = provider.ensureProvisioned(artifact);

    assertThat(first.deploymentName()).isEqualTo(artifact.deploymentName());
    assertThat(first.ready().toCompletableFuture().isDone()).isFalse();
    final var deployment = second.ready().toCompletableFuture().get(5, TimeUnit.SECONDS);
    assertThat(deployment).isEqualTo(new ProviderDeployment("fake", artifact.deploymentName()));
    assertThat(first.ready().toCompletableFuture().get(1, TimeUnit.SECONDS)).isEqualTo(deployment);
  }

  @Test
  void rejectsDependencyManifestsPermanently() {
    final var provider = provider(Duration.ZERO);

    assertThatThrownBy(
            () ->
                provider.ensureProvisioned(
                    artifact(Optional.of("{}".getBytes(StandardCharsets.UTF_8)))))
        .isInstanceOfSatisfying(
            ProvisioningException.class,
            e -> {
              assertThat(e.code()).isEqualTo("DEPENDENCIES_UNSUPPORTED");
              assertThat(e.retryable()).isFalse();
            });
  }

  @Test
  void reportsMissingDeployment() throws Exception {
    final var provider = provider(Duration.ZERO);
    final var artifact = artifact(Optional.empty());
    final var deployment =
        provider.ensureProvisioned(artifact).ready().toCompletableFuture().get(1, TimeUnit.SECONDS);

    provider.delete(deployment.deploymentId());
    final var response = provider.invoke(deployment, request());

    assertThat(response.isDeploymentMissing()).isTrue();
  }

  @Test
  void invokesTheProvisionedScriptLocally() throws Exception {
    final var status = INTERPRETERS.statuses().get(ScriptLanguage.JAVASCRIPT);
    assumeTrue(status.available(), status.diagnostic());
    final var provider = provider(Duration.ZERO);
    final var deployment =
        provider
            .ensureProvisioned(artifact(Optional.empty()))
            .ready()
            .toCompletableFuture()
            .get(1, TimeUnit.SECONDS);

    final var response = provider.invoke(deployment, request());

    assertThat(response.outcome()).isEqualTo(ExecutionResponse.COMPLETED);
    assertThat(response.variables()).containsEntry("sum", 5);
  }

  private FakeManagedCodeProvider provider(Duration provisioningDelay) {
    return new FakeManagedCodeProvider(
        new LocalProcessScriptExecutor(
            OBJECT_MAPPER,
            INTERPRETERS,
            new LocalProcessScriptExecutor.Limits(
                64 * 1_024, 64 * 1_024, 64 * 1_024, 4 * 1_024, Duration.ofSeconds(5)),
            temporaryRoot),
        OBJECT_MAPPER,
        provisioningDelay);
  }

  private static ExecutionRequest request() {
    return new ExecutionRequest("1", Map.of("a", 2, "b", 3), Map.of("jobKey", "1"));
  }

  private static ArtifactSpec artifact(Optional<byte[]> dependencies) {
    return ArtifactSpec.create(
        "default",
        "tenant-a",
        "fake",
        ScriptLanguage.JAVASCRIPT,
        "nodejs22",
        "sum.js",
        SCRIPT.getBytes(StandardCharsets.UTF_8),
        dependencies);
  }
}
