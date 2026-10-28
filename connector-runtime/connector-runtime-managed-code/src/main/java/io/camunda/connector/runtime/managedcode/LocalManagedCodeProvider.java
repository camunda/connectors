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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Trusted-development-only provider that runs the same executor wrapper as the cloud providers, as
 * a local Node.js or Python process with the Connector Runtime operating-system identity. It is
 * resource-bounded but is not a sandbox.
 *
 * <ul>
 *   <li>An executor is the directory {@code <directory>/<executor name>} holding the wrapper. It is
 *       written in a staging directory and renamed atomically, so it is either complete or absent.
 *   <li>Every call starts the wrapper in local mode, with a temporary directory of its own: the
 *       wrapper only kills the process group of the script and removes its own directory, since it
 *       shares the host with other processes.
 *   <li>Dependencies are not supported: a script uses the standard library of the interpreter.
 *   <li>A deleted executor directory is reported as {@link ExecutionResponse#DEPLOYMENT_MISSING}.
 * </ul>
 */
public final class LocalManagedCodeProvider implements ManagedCodeProvider {

  public static final String NAME = "local";

  static final String NAME_PREFIX = "local-";

  private static final Logger LOG = LoggerFactory.getLogger(LocalManagedCodeProvider.class);
  private static final int MAX_RESPONSE_BYTES = 2_000_000;
  private static final int MAX_LOG_CHARS = 4_096;
  private static final Duration PROCESS_MARGIN = Duration.ofSeconds(10);

  private final Map<ScriptLanguage, ExecutorPackage> executors =
      new EnumMap<>(ScriptLanguage.class);
  private final Map<ScriptLanguage, Path> interpreterPaths = new ConcurrentHashMap<>();
  private final LocalInterpreterDiscovery.DiscoveryReport interpreters;
  private final ObjectMapper objectMapper;
  private final Path directory;
  private final Duration executionTimeout;

  LocalManagedCodeProvider(
      LocalInterpreterDiscovery.DiscoveryReport interpreters,
      ObjectMapper objectMapper,
      Path directory,
      Duration executionTimeout) {
    this.interpreters = interpreters;
    this.objectMapper = objectMapper;
    this.directory = directory.toAbsolutePath().normalize();
    this.executionTimeout = executionTimeout;
    for (var language : ScriptLanguage.values()) {
      executors.put(language, ExecutorPackage.local(language));
    }
  }

  @Override
  public String name() {
    return NAME;
  }

  /** Writes the wrapper of each language if absent. A language without interpreter is skipped. */
  @Override
  public void ensureExecutors() {
    for (var executor : executors.values()) {
      final var target = executorDirectory(executor);
      if (!Files.isDirectory(target)) {
        write(executor, target);
      }
    }
  }

  @Override
  public ExecutionResponse execute(ScriptLanguage language, ExecutionRequest request) {
    final var status = interpreters.statuses().get(language);
    if (status == null || !status.available()) {
      return ExecutionResponse.failed(
          "UNSUPPORTED_RUNTIME",
          status == null ? "No %s interpreter".formatted(language.language()) : status.diagnostic(),
          false);
    }
    final var executor = executors.get(language);
    final var executorDirectory = executorDirectory(executor);
    if (!Files.isRegularFile(executorDirectory.resolve(executor.entrypoint()))) {
      return ExecutionResponse.failed(
          ExecutionResponse.DEPLOYMENT_MISSING,
          "Local executor '%s' does not exist".formatted(executorDirectory.getFileName()),
          false);
    }
    Path call = null;
    try {
      call = Files.createTempDirectory(directory, "call-");
      return run(language, executor, executorDirectory, call, request);
    } catch (IOException e) {
      return ExecutionResponse.failed(
          "PLATFORM_EXECUTION_FAILED", "Local executor failed: " + e.getMessage(), false);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return ExecutionResponse.failed(
          "PLATFORM_UNAVAILABLE", "Local execution was interrupted", true);
    } finally {
      if (call != null) {
        deleteRecursively(call);
      }
    }
  }

  private ExecutionResponse run(
      ScriptLanguage language,
      ExecutorPackage executor,
      Path executorDirectory,
      Path call,
      ExecutionRequest request)
      throws IOException, InterruptedException {
    final var interpreter = interpreterPath(language);
    final var entrypoint = executorDirectory.resolve(executor.entrypoint()).toString();
    final List<String> command =
        switch (language) {
          case PYTHON -> List.of(interpreter.toString(), "-I", entrypoint);
          case JAVASCRIPT -> List.of(interpreter.toString(), entrypoint);
        };
    final var scratch = Files.createDirectory(call.resolve("tmp"));
    final var requestFile = call.resolve("request.json");
    final var responseFile = call.resolve("response.json");
    final var logFile = call.resolve("wrapper.log");
    objectMapper.writeValue(requestFile.toFile(), request.payload());
    final var processBuilder =
        new ProcessBuilder(command)
            .directory(call.toFile())
            .redirectInput(requestFile.toFile())
            .redirectOutput(responseFile.toFile())
            .redirectError(logFile.toFile());
    final var environment = processBuilder.environment();
    LocalProcessEnvironment.configure(environment, scratch);
    environment.put("EXECUTOR_LOCAL", "1");
    environment.put("EXECUTOR_TMP", scratch.toString());
    environment.put("EXECUTOR_DEADLINE_MS", Long.toString(executionTimeout.toMillis()));
    // The script process gets this PATH, so that it finds the interpreter it runs on.
    environment.put(
        "EXECUTOR_CHILD_PATH",
        interpreter.getParent() + File.pathSeparator + "/usr/bin" + File.pathSeparator + "/bin");
    final var process = processBuilder.start();
    if (!process.waitFor(executionTimeout.plus(PROCESS_MARGIN).toMillis(), TimeUnit.MILLISECONDS)) {
      process.descendants().forEach(ProcessHandle::destroyForcibly);
      process.destroyForcibly();
      process.waitFor();
      return ExecutionResponse.failed(
          "PLATFORM_EXECUTION_FAILED", "The local executor did not answer in time", false);
    }
    final var logs = readBounded(logFile, MAX_LOG_CHARS);
    if (!logs.isBlank()) {
      LOG.debug("Local {} executor: {}", language.language(), logs);
    }
    final byte[] body = Files.readAllBytes(responseFile);
    if (body.length == 0 || body.length > MAX_RESPONSE_BYTES) {
      return ExecutionResponse.failed(
          "PLATFORM_EXECUTION_FAILED",
          "The local executor exited with %d and no valid response: %s"
              .formatted(process.exitValue(), logs),
          false);
    }
    try {
      return objectMapper.readValue(body, ExecutionResponse.class);
    } catch (IOException e) {
      return ExecutionResponse.failed(
          "INVALID_RESULT", "The local executor returned no valid contract response", false);
    }
  }

  /**
   * The interpreter the wrapper runs on. A version manager such as asdf puts a shim on {@code PATH}
   * that needs the host environment, which the wrapper does not get, so the real executable is
   * asked once, with the host environment.
   */
  private Path interpreterPath(ScriptLanguage language) throws IOException, InterruptedException {
    final var known = interpreterPaths.get(language);
    if (known != null) {
      return known;
    }
    final var discovered = interpreters.require(language).executable().orElseThrow();
    final var probe =
        switch (language) {
          case PYTHON -> List.of(discovered.toString(), "-c", "import sys; print(sys.executable)");
          case JAVASCRIPT -> List.of(discovered.toString(), "-p", "process.execPath");
        };
    final var process = new ProcessBuilder(probe).redirectErrorStream(true).start();
    Path resolved = discovered;
    if (process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0) {
      final var output =
          new String(process.getInputStream().readNBytes(4_096), StandardCharsets.UTF_8).strip();
      if (!output.isEmpty() && Files.isExecutable(Path.of(output))) {
        resolved = Path.of(output);
      }
    } else {
      process.destroyForcibly();
    }
    interpreterPaths.put(language, resolved);
    return resolved;
  }

  private Path executorDirectory(ExecutorPackage executor) {
    return directory.resolve(executor.functionName(NAME_PREFIX));
  }

  private void write(ExecutorPackage executor, Path target) {
    try {
      Files.createDirectories(directory);
      final var staging = directory.resolve(".staging-" + UUID.randomUUID());
      Files.createDirectory(staging);
      try {
        for (var file : executor.files().entrySet()) {
          Files.write(staging.resolve(file.getKey()), file.getValue());
        }
        Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
        LOG.info("Wrote local executor {}", target);
      } catch (IOException e) {
        if (!Files.isDirectory(target)) {
          throw e;
        }
        // Another runtime instance wrote it first.
      } finally {
        deleteRecursively(staging);
      }
    } catch (IOException e) {
      throw new ProvisioningException(
          "PLATFORM_UNAVAILABLE",
          "Cannot write the local executor to %s: %s".formatted(target, e.getMessage()),
          false,
          e);
    }
  }

  private static String readBounded(Path file, int maxChars) throws IOException {
    if (!Files.isRegularFile(file)) {
      return "";
    }
    final var text = Files.readString(file, StandardCharsets.UTF_8).strip();
    return text.length() <= maxChars ? text : text.substring(0, maxChars);
  }

  private static void deleteRecursively(Path path) {
    if (!Files.exists(path)) {
      return;
    }
    try (Stream<Path> paths = Files.walk(path)) {
      paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
    } catch (IOException e) {
      LOG.debug("Could not delete {}", path, e);
    }
  }
}
