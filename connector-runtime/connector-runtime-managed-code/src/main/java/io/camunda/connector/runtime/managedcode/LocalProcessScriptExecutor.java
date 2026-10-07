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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Trusted-development-only local script executor. It runs scripts in Node.js or Python subprocesses
 * with the Connector Runtime operating-system identity. It is resource-bounded but is not a
 * sandbox.
 */
final class LocalProcessScriptExecutor {

  private static final String CONTRACT_VERSION = ExecutionRequest.CONTRACT_VERSION;
  private static final Duration TERMINATION_GRACE_PERIOD = Duration.ofMillis(250);
  private static final Duration STREAM_COLLECTION_TIMEOUT = Duration.ofSeconds(5);

  private final ObjectMapper objectMapper;
  private final LocalInterpreterDiscovery.DiscoveryReport interpreters;
  private final Limits limits;
  private final Path temporaryRoot;

  LocalProcessScriptExecutor(
      ObjectMapper objectMapper,
      LocalInterpreterDiscovery.DiscoveryReport interpreters,
      Duration timeout) {
    this(
        objectMapper,
        interpreters,
        new Limits(256 * 1_024, 1_024 * 1_024, 1_024 * 1_024, 64 * 1_024, timeout),
        Path.of(System.getProperty("java.io.tmpdir")));
  }

  LocalProcessScriptExecutor(
      ObjectMapper objectMapper,
      LocalInterpreterDiscovery.DiscoveryReport interpreters,
      Limits limits,
      Path temporaryRoot) {
    this.objectMapper = objectMapper;
    this.interpreters = interpreters;
    this.limits = limits;
    this.temporaryRoot = temporaryRoot.toAbsolutePath().normalize();
  }

  ExecutionResult execute(ScriptLanguage language, byte[] source, ExecutionRequest request)
      throws IOException, InterruptedException {
    return execute(language, source, request, Optional.empty());
  }

  /**
   * @param dependencies directory with installed packages: added to {@code PYTHONPATH} for Python,
   *     linked as {@code node_modules} next to the script for JavaScript, because ES modules ignore
   *     {@code NODE_PATH}
   */
  ExecutionResult execute(
      ScriptLanguage language, byte[] source, ExecutionRequest request, Optional<Path> dependencies)
      throws IOException, InterruptedException {
    final var interpreter = interpreters.require(language);
    if (source.length > limits.maxScriptBytes()) {
      throw new IllegalArgumentException(
          "Script exceeds the %d byte limit".formatted(limits.maxScriptBytes()));
    }
    final var requestBytes = createRequest(request);
    if (requestBytes.length > limits.maxInputBytes()) {
      throw new IllegalArgumentException(
          "Invocation input exceeds the %d byte limit".formatted(limits.maxInputBytes()));
    }

    Files.createDirectories(temporaryRoot);
    final var workingDirectory =
        Files.createTempDirectory(temporaryRoot, "managed-script-invocation-");
    Process process = null;
    Throwable failure = null;
    try {
      final var wrapper = materializeInvocation(language, source, requestBytes, workingDirectory);
      final var processBuilder =
          new ProcessBuilder(
                  interpreter.executable().orElseThrow().toString(),
                  wrapper.getFileName().toString())
              .directory(workingDirectory.toFile());
      LocalProcessEnvironment.configure(processBuilder.environment(), workingDirectory);
      if (dependencies.isPresent()) {
        final var packages = dependencies.get().toAbsolutePath().normalize();
        if (language == ScriptLanguage.PYTHON) {
          processBuilder.environment().put("PYTHONPATH", packages.toString());
        } else {
          Files.createSymbolicLink(workingDirectory.resolve("node_modules"), packages);
        }
      }

      final var startedAt = System.nanoTime();
      process = processBuilder.start();
      final var captured = captureProcess(process, limits.maxOutputBytes(), limits.maxLogBytes());
      final var durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
      return new ExecutionResult(normalizeResponse(captured, durationMillis), durationMillis);
    } catch (IOException | InterruptedException | RuntimeException | Error error) {
      failure = error;
      throw error;
    } finally {
      if (process != null && process.isAlive()) {
        terminateProcessTree(process);
      }
      try {
        deleteRecursively(workingDirectory);
      } catch (IOException cleanupError) {
        if (failure != null) {
          failure.addSuppressed(cleanupError);
        } else {
          throw cleanupError;
        }
      }
    }
  }

  private byte[] createRequest(ExecutionRequest request) throws JsonProcessingException {
    final ObjectNode json = objectMapper.createObjectNode();
    json.put("contractVersion", request.contractVersion());
    json.put("executionId", request.executionId());
    json.set("variables", objectMapper.valueToTree(request.variables()));
    json.set("context", objectMapper.valueToTree(request.context()));
    return objectMapper.writeValueAsBytes(json);
  }

  private Path materializeInvocation(
      ScriptLanguage language, byte[] source, byte[] request, Path workingDirectory)
      throws IOException {
    final var scriptName = language == ScriptLanguage.PYTHON ? "user_script.py" : "user_script.mjs";
    Files.write(
        workingDirectory.resolve(scriptName),
        source,
        StandardOpenOption.CREATE_NEW,
        StandardOpenOption.WRITE);
    Files.write(
        workingDirectory.resolve("request.json"),
        request,
        StandardOpenOption.CREATE_NEW,
        StandardOpenOption.WRITE);

    final var wrapper = workingDirectory.resolve(language.wrapperResource());
    Files.write(
        wrapper, language.wrapper(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    return wrapper;
  }

  private CapturedProcess captureProcess(Process process, int maxOutputBytes, int maxLogBytes)
      throws InterruptedException, IOException {
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      final Future<CapturedBytes> output =
          executor.submit(() -> readBounded(process.getInputStream(), maxOutputBytes));
      final Future<CapturedBytes> logs =
          executor.submit(() -> readBounded(process.getErrorStream(), maxLogBytes));
      final var completed = process.waitFor(limits.maxDuration().toMillis(), TimeUnit.MILLISECONDS);
      if (!completed) {
        terminateProcessTree(process);
      }
      try {
        return new CapturedProcess(
            completed,
            completed ? process.exitValue() : -1,
            output.get(STREAM_COLLECTION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS),
            logs.get(STREAM_COLLECTION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
      } catch (ExecutionException e) {
        throw new IOException("Failed to collect local process output", e.getCause());
      } catch (TimeoutException e) {
        terminateProcessTree(process);
        throw new IOException("Timed out while collecting local process output", e);
      }
    }
  }

  private JsonNode normalizeResponse(CapturedProcess captured, long durationMillis) {
    if (!captured.completed()) {
      return failureResponse(
          "TIMEOUT",
          "Execution exceeded the %d ms limit".formatted(limits.maxDuration().toMillis()),
          false,
          false);
    }
    if (captured.output().truncated()) {
      return failureResponse(
          "RESOURCE_LIMIT",
          "Execution output exceeded the %d byte limit".formatted(limits.maxOutputBytes()),
          false,
          true);
    }
    if (captured.exitCode() != 0) {
      return failureResponse(
          "PLATFORM_UNAVAILABLE",
          "Local interpreter exited with code %d after %d ms"
              .formatted(captured.exitCode(), durationMillis),
          true,
          false);
    }
    final JsonNode response;
    try {
      response = objectMapper.readTree(captured.output().value());
    } catch (JsonProcessingException e) {
      return failureResponse(
          "INVALID_RESULT", "Local runtime response is not valid JSON", false, false);
    }
    if (!isValidContractResponse(response)) {
      return failureResponse(
          "INVALID_RESULT", "Local runtime response violates contract version 1", false, false);
    }
    return response;
  }

  private static boolean isValidContractResponse(JsonNode response) {
    if (response == null
        || !response.isObject()
        || !CONTRACT_VERSION.equals(response.path("contractVersion").asText())) {
      return false;
    }
    final var outcome = response.path("outcome").asText();
    return ("COMPLETED".equals(outcome) && response.path("variables").isObject())
        || ("FAILED".equals(outcome) && response.path("error").isObject());
  }

  private ObjectNode failureResponse(
      String code, String message, boolean retryable, boolean truncated) {
    final var response = objectMapper.createObjectNode();
    response.put("contractVersion", CONTRACT_VERSION);
    response.put("outcome", "FAILED");
    final var error = response.putObject("error");
    error.put("code", code);
    error.put("message", message);
    error.put("retryable", retryable);
    error.put("truncated", truncated);
    return response;
  }

  static CapturedBytes readBounded(InputStream input, int limit) throws IOException {
    final var retained = new ByteArrayOutputStream(Math.min(limit, 8_192));
    final var buffer = new byte[8_192];
    long total = 0;
    int read;
    while ((read = input.read(buffer)) != -1) {
      if (retained.size() < limit) {
        retained.write(buffer, 0, Math.min(read, limit - retained.size()));
      }
      total += read;
    }
    return new CapturedBytes(retained.toString(StandardCharsets.UTF_8), total > limit, total);
  }

  static void terminateProcessTree(Process process) throws InterruptedException {
    final var root = process.toHandle();
    final List<ProcessHandle> descendants = new ArrayList<>(root.descendants().toList());
    descendants.sort(Comparator.comparingLong(ProcessHandle::pid).reversed());
    descendants.forEach(ProcessHandle::destroy);
    root.destroy();
    if (!process.waitFor(TERMINATION_GRACE_PERIOD.toMillis(), TimeUnit.MILLISECONDS)) {
      descendants.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
      root.descendants().forEach(ProcessHandle::destroyForcibly);
      root.destroyForcibly();
      process.waitFor(2, TimeUnit.SECONDS);
    }
  }

  /** Removes symbolic links themselves, never their targets. */
  static void deleteRecursively(Path directory) throws IOException {
    if (!Files.exists(directory)) {
      return;
    }
    try (var paths = Files.walk(directory)) {
      for (var path : paths.sorted(Comparator.reverseOrder()).toList()) {
        Files.delete(path);
      }
    }
  }

  record Limits(
      int maxScriptBytes,
      int maxInputBytes,
      int maxOutputBytes,
      int maxLogBytes,
      Duration maxDuration) {

    Limits {
      if (maxScriptBytes < 1
          || maxInputBytes < 1
          || maxOutputBytes < 1
          || maxLogBytes < 1
          || maxDuration.isZero()
          || maxDuration.isNegative()) {
        throw new IllegalArgumentException("All local execution limits must be positive");
      }
    }
  }

  record ExecutionResult(JsonNode response, long durationMillis) {}

  private record CapturedProcess(
      boolean completed, int exitCode, CapturedBytes output, CapturedBytes logs) {}

  record CapturedBytes(String value, boolean truncated, long totalBytes) {}
}
