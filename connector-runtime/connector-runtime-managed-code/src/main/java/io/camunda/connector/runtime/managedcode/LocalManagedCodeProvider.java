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
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Trusted-development-only provider that deploys artifacts to a local directory and runs them in
 * Node.js or Python subprocesses with the Connector Runtime operating-system identity. It is
 * resource-bounded but is not a sandbox.
 *
 * <p>It follows the provider contract like a cloud provider would, so it doubles as a reference
 * implementation:
 *
 * <ul>
 *   <li>A deployment is the directory {@code <directory>/<deploymentName>}. It is built in a
 *       staging directory and renamed atomically, so a deployment is either complete or absent.
 *   <li>An existing deployment is adopted without rebuilding it, also after a restart.
 *   <li>A dependency manifest is installed once, while provisioning: {@code pip install --target}
 *       for {@code requirements.txt}, {@code npm install --ignore-scripts} for {@code
 *       package.json}. Installation runs with the host environment and network access, so registry
 *       and proxy settings apply. Invocations never install anything.
 *   <li>A deleted directory is reported as {@link ExecutionResponse#DEPLOYMENT_MISSING}.
 * </ul>
 *
 * <p>Provisioning completes synchronously, before {@link #ensureProvisioned(ArtifactSpec)} returns.
 * A cloud provider returns once the provider accepted the work and completes the handle later.
 */
public final class LocalManagedCodeProvider implements ManagedCodeProvider {

  public static final String NAME = "local";

  static final String METADATA_FILE = "deployment.json";

  private static final int MAX_INSTALL_LOG_CHARS = 2_000;

  private record DeploymentMetadata(String language, String runtime, String digest) {}

  private final LocalProcessScriptExecutor executor;
  private final LocalInterpreterDiscovery.DiscoveryReport interpreters;
  private final ObjectMapper objectMapper;
  private final Path directory;
  private final Duration installTimeout;

  LocalManagedCodeProvider(
      LocalProcessScriptExecutor executor,
      LocalInterpreterDiscovery.DiscoveryReport interpreters,
      ObjectMapper objectMapper,
      Path directory,
      Duration installTimeout) {
    this.executor = executor;
    this.interpreters = interpreters;
    this.objectMapper = objectMapper;
    this.directory = directory.toAbsolutePath().normalize();
    this.installTimeout = installTimeout;
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public ProvisioningHandle ensureProvisioned(ArtifactSpec artifact) {
    final var deployment = new ProviderDeployment(NAME, artifact.deploymentName());
    final var target = deploymentDirectory(artifact.deploymentName());
    if (!Files.isRegularFile(target.resolve(METADATA_FILE))) {
      build(artifact, target);
    }
    return new ProvisioningHandle(
        artifact.deploymentName(), CompletableFuture.completedFuture(deployment));
  }

  @Override
  public ExecutionResponse invoke(ProviderDeployment deployment, ExecutionRequest request) {
    final var target = deploymentDirectory(deployment.deploymentId());
    final DeploymentMetadata metadata;
    final byte[] script;
    try {
      metadata =
          objectMapper.readValue(target.resolve(METADATA_FILE).toFile(), DeploymentMetadata.class);
      script = Files.readAllBytes(target.resolve(scriptFileName(language(metadata))));
    } catch (IOException e) {
      return ExecutionResponse.failed(
          ExecutionResponse.DEPLOYMENT_MISSING,
          "Provider deployment '%s' does not exist".formatted(deployment.deploymentId()),
          false);
    }
    final var language = language(metadata);
    final var packages = target.resolve(packagesDirectoryName(language));
    try {
      final var result =
          executor.execute(
              language,
              script,
              request,
              Files.isDirectory(packages) ? Optional.of(packages) : Optional.empty());
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

  @Override
  public void delete(ProviderDeployment deployment) {
    try {
      LocalProcessScriptExecutor.deleteRecursively(deploymentDirectory(deployment.deploymentId()));
    } catch (IOException e) {
      throw new IllegalStateException(
          "Failed to delete provider deployment '%s'".formatted(deployment.deploymentId()), e);
    }
  }

  private void build(ArtifactSpec artifact, Path target) {
    try {
      interpreters.require(artifact.language());
    } catch (IllegalStateException e) {
      throw new ProvisioningException("PLATFORM_UNAVAILABLE", e.getMessage(), true);
    }
    Path staging = null;
    try {
      Files.createDirectories(directory);
      staging =
          Files.createDirectory(
              directory.resolve(".staging-" + artifact.deploymentName() + "-" + UUID.randomUUID()));
      Files.write(staging.resolve(scriptFileName(artifact.language())), artifact.script());
      if (artifact.dependencies().isPresent()) {
        install(artifact.language(), artifact.dependencies().get(), staging);
      }
      // Written last: a deployment directory with metadata is complete.
      objectMapper.writeValue(
          staging.resolve(METADATA_FILE).toFile(),
          new DeploymentMetadata(
              artifact.language().language(), artifact.runtime(), artifact.digest()));
      try {
        Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
      } catch (IOException e) {
        // The exception type of a lost race differs by platform: adopt whatever is complete.
        if (!Files.isRegularFile(target.resolve(METADATA_FILE))) {
          throw e;
        }
      }
    } catch (IOException e) {
      throw new ProvisioningException(
          "PLATFORM_UNAVAILABLE",
          "Failed to create local deployment '%s': %s"
              .formatted(artifact.deploymentName(), e.getMessage()),
          true,
          e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ProvisioningException(
          "PLATFORM_UNAVAILABLE", "Dependency installation was interrupted", true);
    } finally {
      if (staging != null) {
        try {
          LocalProcessScriptExecutor.deleteRecursively(staging);
        } catch (IOException ignored) {
          // A leftover staging directory is harmless; it is never adopted.
        }
      }
    }
  }

  /**
   * Install failures are retryable: the registry keeps permanent failures until restart, and a
   * network failure must not block the artifact that long.
   */
  private void install(ScriptLanguage language, byte[] manifest, Path staging)
      throws IOException, InterruptedException {
    final List<String> command = new ArrayList<>();
    final var interpreter = interpreters.require(language).executable().orElseThrow();
    if (language == ScriptLanguage.PYTHON) {
      Files.write(staging.resolve("requirements.txt"), manifest);
      command.addAll(
          List.of(
              interpreter.toString(),
              "-m",
              "pip",
              "install",
              "--target",
              packagesDirectoryName(language),
              "--requirement",
              "requirements.txt",
              "--no-input",
              "--disable-pip-version-check",
              "--no-warn-script-location"));
    } else {
      Files.write(staging.resolve("package.json"), manifest);
      command.addAll(
          List.of(
              npmExecutable(interpreter).toString(),
              "install",
              "--omit=dev",
              "--ignore-scripts",
              "--no-audit",
              "--no-fund"));
    }

    final var processBuilder =
        new ProcessBuilder(command).directory(staging.toFile()).redirectErrorStream(true);
    // npm starts through "#!/usr/bin/env node": put the discovered interpreter first on PATH.
    final var environment = processBuilder.environment();
    environment.put(
        "PATH",
        interpreter.getParent() + File.pathSeparator + environment.getOrDefault("PATH", ""));

    final var process = processBuilder.start();
    try (var reader = Executors.newVirtualThreadPerTaskExecutor()) {
      final var output =
          reader.submit(
              () -> LocalProcessScriptExecutor.readBounded(process.getInputStream(), 64 * 1_024));
      if (!process.waitFor(installTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
        LocalProcessScriptExecutor.terminateProcessTree(process);
        throw new ProvisioningException(
            "DEPENDENCY_INSTALL_FAILED",
            "Dependency installation exceeded %d s".formatted(installTimeout.toSeconds()),
            true);
      }
      if (process.exitValue() != 0) {
        throw new ProvisioningException(
            "DEPENDENCY_INSTALL_FAILED",
            "'%s' exited with code %d: %s"
                .formatted(
                    String.join(" ", command.subList(1, command.size())),
                    process.exitValue(),
                    tail(output.get(5, TimeUnit.SECONDS).value())),
            true);
      }
    } catch (ExecutionException | TimeoutException e) {
      throw new IOException("Failed to collect dependency installation output", e);
    } finally {
      if (process.isAlive()) {
        LocalProcessScriptExecutor.terminateProcessTree(process);
      }
    }
  }

  private Path deploymentDirectory(String deploymentName) {
    final var target = directory.resolve(deploymentName).normalize();
    if (!target.getParent().equals(directory)) {
      throw new IllegalArgumentException("Invalid deployment name: " + deploymentName);
    }
    return target;
  }

  private static ScriptLanguage language(DeploymentMetadata metadata) {
    return ScriptLanguage.valueOf(metadata.language().toUpperCase(Locale.ROOT));
  }

  /** The same names that {@link LocalProcessScriptExecutor} uses for the invocation. */
  private static String scriptFileName(ScriptLanguage language) {
    return language == ScriptLanguage.PYTHON ? "user_script.py" : "user_script.mjs";
  }

  private static String packagesDirectoryName(ScriptLanguage language) {
    return language == ScriptLanguage.PYTHON ? "site-packages" : "node_modules";
  }

  private static Path npmExecutable(Path node) {
    final var name = isWindows() ? "npm.cmd" : "npm";
    final var sibling = node.resolveSibling(name);
    if (Files.isExecutable(sibling)) {
      return sibling;
    }
    final var path = System.getenv("PATH");
    if (path != null) {
      for (var entry : path.split(File.pathSeparator)) {
        final var candidate = Path.of(entry, name);
        if (!entry.isBlank() && Files.isExecutable(candidate)) {
          return candidate;
        }
      }
    }
    throw new ProvisioningException(
        "PLATFORM_UNAVAILABLE",
        "npm was not found next to %s or on PATH; it is needed to install package.json"
            .formatted(node),
        true);
  }

  private static String tail(String output) {
    final var trimmed = output.strip();
    return trimmed.length() <= MAX_INSTALL_LOG_CHARS
        ? trimmed
        : "..." + trimmed.substring(trimmed.length() - MAX_INSTALL_LOG_CHARS);
  }

  private static boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
  }
}
