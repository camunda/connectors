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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalManagedCodeProviderTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final LocalInterpreterDiscovery.DiscoveryReport INTERPRETERS =
      LocalInterpreterDiscovery.discover();
  private static final String SUM_SCRIPT =
      """
      export async function execute(variables) {
        return { sum: variables.a + variables.b };
      }
      """;

  @TempDir Path temporaryRoot;
  @TempDir Path deployments;
  @TempDir Path packages;

  @Test
  void deploysToADirectoryAndInvokesTheDeployment() throws Exception {
    assumeInterpreter(ScriptLanguage.JAVASCRIPT);
    final var provider = provider(INTERPRETERS);
    final var artifact = javascript(SUM_SCRIPT, Optional.empty());

    final var deployment = provision(provider, artifact);
    final var response = provider.invoke(deployment, request(Map.of("a", 2, "b", 3)));

    assertThat(deployment).isEqualTo(new ProviderDeployment("local", artifact.deploymentName()));
    assertThat(deployments.resolve(artifact.deploymentName()).resolve("deployment.json"))
        .isRegularFile();
    assertThat(response.outcome()).isEqualTo(ExecutionResponse.COMPLETED);
    assertThat(response.variables()).containsEntry("sum", 5);
  }

  @Test
  void adoptsAnExistingDeploymentWithoutRebuildingIt() throws Exception {
    assumeInterpreter(ScriptLanguage.JAVASCRIPT);
    final var artifact = javascript(SUM_SCRIPT, Optional.empty());
    provision(provider(INTERPRETERS), artifact);

    // A restarted runtime without interpreters cannot build, so success means adoption.
    final var restarted = provider(new LocalInterpreterDiscovery.DiscoveryReport(Map.of()));
    final var deployment = provision(restarted, artifact);

    assertThat(deployment.deploymentId()).isEqualTo(artifact.deploymentName());
  }

  @Test
  void concurrentProvisioningsConvergeOnOneDeployment() throws Exception {
    assumeInterpreter(ScriptLanguage.JAVASCRIPT);
    final var artifact = javascript(SUM_SCRIPT, Optional.empty());
    final var first = provider(INTERPRETERS);
    final var second = provider(INTERPRETERS);
    final var start = new java.util.concurrent.CountDownLatch(1);

    try (var executor = java.util.concurrent.Executors.newFixedThreadPool(8)) {
      final var results =
          java.util.stream.IntStream.range(0, 8)
              .mapToObj(
                  i ->
                      executor.submit(
                          () -> {
                            start.await();
                            return provision(i % 2 == 0 ? first : second, artifact);
                          }))
              .toList();
      start.countDown();
      for (var result : results) {
        assertThat(result.get(10, TimeUnit.SECONDS).deploymentId())
            .isEqualTo(artifact.deploymentName());
      }
    }
    try (var entries = Files.list(deployments)) {
      assertThat(entries.map(path -> path.getFileName().toString()))
          .containsExactly(artifact.deploymentName());
    }
  }

  @Test
  void reportsADeletedDeploymentAsMissing() throws Exception {
    assumeInterpreter(ScriptLanguage.JAVASCRIPT);
    final var provider = provider(INTERPRETERS);
    final var deployment = provision(provider, javascript(SUM_SCRIPT, Optional.empty()));

    provider.delete(deployment);
    provider.delete(deployment);
    final var response = provider.invoke(deployment, request(Map.of("a", 2, "b", 3)));

    assertThat(response.isDeploymentMissing()).isTrue();
    assertThat(deployments).isEmptyDirectory();
  }

  @Test
  void installsNpmDependenciesOnceAndKeepsThemAcrossInvocations() throws Exception {
    assumeInterpreter(ScriptLanguage.JAVASCRIPT);
    final var greet = Files.createDirectory(packages.resolve("greet"));
    Files.writeString(
        greet.resolve("package.json"),
        """
        {"name": "greet", "version": "1.0.0", "main": "index.js"}
        """);
    Files.writeString(greet.resolve("index.js"), "module.exports = (name) => `Hello ${name}`;\n");
    final var manifest =
        """
        {"name": "script", "version": "1.0.0", "dependencies": {"greet": "file:%s"}}
        """
            .formatted(greet.toAbsolutePath().toString().replace("\\", "/"));
    final var script =
        """
        import greet from 'greet';
        export function execute(variables) {
          return { message: greet(variables.name) };
        }
        """;
    final var provider = provider(INTERPRETERS);
    final var artifact = javascript(script, Optional.of(manifest));

    final var deployment = provision(provider, artifact);
    final var first = provider.invoke(deployment, request(Map.of("name", "Ada")));
    final var second = provider.invoke(deployment, request(Map.of("name", "Grace")));

    assertThat(first.variables()).containsEntry("message", "Hello Ada");
    assertThat(second.variables()).containsEntry("message", "Hello Grace");
    // Removing the invocation's node_modules link must not remove the installed packages.
    assertThat(deployments.resolve(artifact.deploymentName()).resolve("node_modules/greet"))
        .exists();
  }

  @Test
  void installsPipRequirementsIntoTheDeployment() throws Exception {
    assumeInterpreter(ScriptLanguage.PYTHON);
    final var wheel =
        writeWheel(packages, "greet", "def greet(name):\n    return f'Hello {name}'\n");
    final var script =
        """
        from greet import greet

        def execute(variables, context):
            return {"message": greet(variables["name"])}
        """;
    final var provider = provider(INTERPRETERS);
    final var artifact = python(script, Optional.of(wheel.toAbsolutePath() + "\n"));

    final var deployment = provision(provider, artifact);
    final var response = provider.invoke(deployment, request(Map.of("name", "Ada")));

    assertThat(response.outcome()).isEqualTo(ExecutionResponse.COMPLETED);
    assertThat(response.variables()).containsEntry("message", "Hello Ada");
  }

  @Test
  void failedInstallationIsRetryableAndLeavesNoDeployment() {
    assumeInterpreter(ScriptLanguage.PYTHON);
    final var provider = provider(INTERPRETERS);
    final var missingWheel = packages.resolve("missing-1.0-py3-none-any.whl").toAbsolutePath();

    assertThatThrownBy(
            () ->
                provider.ensureProvisioned(
                    python(
                        "def execute(v, c):\n    return {}\n", Optional.of(missingWheel + "\n"))))
        .isInstanceOfSatisfying(
            ProvisioningException.class,
            e -> {
              assertThat(e.code()).isEqualTo("DEPENDENCY_INSTALL_FAILED");
              assertThat(e.retryable()).isTrue();
            });
    assertThat(deployments).isEmptyDirectory();
  }

  @Test
  void failsRetryablyWithoutInterpreter() {
    final var provider = provider(new LocalInterpreterDiscovery.DiscoveryReport(Map.of()));

    assertThatThrownBy(() -> provider.ensureProvisioned(javascript(SUM_SCRIPT, Optional.empty())))
        .isInstanceOfSatisfying(
            ProvisioningException.class,
            e -> {
              assertThat(e.code()).isEqualTo("PLATFORM_UNAVAILABLE");
              assertThat(e.retryable()).isTrue();
            });
  }

  private LocalManagedCodeProvider provider(
      LocalInterpreterDiscovery.DiscoveryReport interpreters) {
    return new LocalManagedCodeProvider(
        new LocalProcessScriptExecutor(
            OBJECT_MAPPER,
            interpreters,
            new LocalProcessScriptExecutor.Limits(
                64 * 1_024, 64 * 1_024, 64 * 1_024, 4 * 1_024, Duration.ofSeconds(10)),
            temporaryRoot),
        interpreters,
        OBJECT_MAPPER,
        deployments,
        Duration.ofMinutes(2));
  }

  private static ProviderDeployment provision(
      LocalManagedCodeProvider provider, ArtifactSpec artifact) throws Exception {
    return provider
        .ensureProvisioned(artifact)
        .ready()
        .toCompletableFuture()
        .get(1, TimeUnit.SECONDS);
  }

  private static void assumeInterpreter(ScriptLanguage language) {
    final var status = INTERPRETERS.statuses().get(language);
    assumeTrue(status.available(), status.diagnostic());
  }

  private static ExecutionRequest request(Map<String, Object> variables) {
    return new ExecutionRequest("1", variables, Map.of("jobKey", "1"));
  }

  private static ArtifactSpec javascript(String script, Optional<String> packageJson) {
    return artifact(ScriptLanguage.JAVASCRIPT, "nodejs22", "script.js", script, packageJson);
  }

  private static ArtifactSpec python(String script, Optional<String> requirements) {
    return artifact(ScriptLanguage.PYTHON, "python3", "script.py", script, requirements);
  }

  private static ArtifactSpec artifact(
      ScriptLanguage language,
      String runtime,
      String resourceName,
      String script,
      Optional<String> manifest) {
    return ArtifactSpec.create(
        "default",
        "tenant-a",
        "local",
        language,
        runtime,
        resourceName,
        script.getBytes(StandardCharsets.UTF_8),
        manifest.map(value -> value.getBytes(StandardCharsets.UTF_8)));
  }

  /** A minimal pure-Python wheel, so that pip installs it without an index or a build backend. */
  private static Path writeWheel(Path directory, String module, String source) throws IOException {
    final var wheel = directory.resolve(module + "-1.0-py3-none-any.whl");
    final var distInfo = module + "-1.0.dist-info/";
    try (var zip = new ZipOutputStream(Files.newOutputStream(wheel))) {
      write(zip, module + ".py", source);
      write(
          zip,
          distInfo + "METADATA",
          "Metadata-Version: 2.1\nName: %s\nVersion: 1.0\n".formatted(module));
      write(
          zip,
          distInfo + "WHEEL",
          "Wheel-Version: 1.0\nGenerator: test\nRoot-Is-Purelib: true\nTag: py3-none-any\n");
      write(
          zip,
          distInfo + "RECORD",
          "%s.py,,\n%sMETADATA,,\n%sWHEEL,,\n%sRECORD,,\n"
              .formatted(module, distInfo, distInfo, distInfo));
    }
    return wheel;
  }

  private static void write(ZipOutputStream zip, String name, String content) throws IOException {
    zip.putNextEntry(new ZipEntry(name));
    zip.write(content.getBytes(StandardCharsets.UTF_8));
    zip.closeEntry();
  }
}
