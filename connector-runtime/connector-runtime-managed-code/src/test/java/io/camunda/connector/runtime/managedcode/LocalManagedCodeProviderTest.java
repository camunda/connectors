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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Runs the executor wrapper with the local interpreters, as the cloud executors run it. */
class LocalManagedCodeProviderTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final LocalInterpreterDiscovery.DiscoveryReport INTERPRETERS =
      LocalInterpreterDiscovery.discover();

  @TempDir Path directory;

  static java.util.stream.Stream<Arguments> scenarios() {
    return java.util.stream.Stream.of(
        Arguments.of(
            ScriptLanguage.PYTHON,
            "def execute(variables, context):\n    print('to the logs')\n    return {'sum': variables['a'] + variables['b']}\n",
            "COMPLETED",
            null),
        Arguments.of(
            ScriptLanguage.JAVASCRIPT,
            "export async function execute(variables) {\n  console.log('to the logs');\n  return { sum: variables.a + variables.b };\n}\n",
            "COMPLETED",
            null),
        Arguments.of(
            ScriptLanguage.PYTHON, "def execute(variables, context)\n", "FAILED", "SCRIPT_ERROR"),
        Arguments.of(
            ScriptLanguage.JAVASCRIPT, "export function execute( {", "FAILED", "SCRIPT_ERROR"),
        Arguments.of(
            ScriptLanguage.PYTHON,
            "def execute(variables, context):\n    raise ValueError('boom')\n",
            "FAILED",
            "SCRIPT_ERROR"),
        Arguments.of(
            ScriptLanguage.JAVASCRIPT,
            "export function execute() { throw new Error('boom'); }",
            "FAILED",
            "SCRIPT_ERROR"),
        Arguments.of(
            ScriptLanguage.PYTHON,
            "def execute(variables, context):\n    return 3\n",
            "FAILED",
            "INVALID_RESULT"),
        Arguments.of(
            ScriptLanguage.JAVASCRIPT,
            "export function execute() { return 3; }",
            "FAILED",
            "INVALID_RESULT"),
        Arguments.of(
            ScriptLanguage.PYTHON,
            "import time\ndef execute(variables, context):\n    time.sleep(30)\n",
            "FAILED",
            "TIMEOUT"),
        Arguments.of(
            ScriptLanguage.JAVASCRIPT,
            "export async function execute() { await new Promise((r) => setTimeout(r, 30000)); }",
            "FAILED",
            "TIMEOUT"));
  }

  @ParameterizedTest
  @MethodSource("scenarios")
  void runsTheScriptThroughTheExecutor(
      ScriptLanguage language, String script, String outcome, String code) {
    assumeInterpreter(language);
    final var provider = provider(Duration.ofSeconds(2));
    provider.ensureExecutors();

    final var response = provider.execute(language, request(script, Map.of("a", 2, "b", 3)));

    assertThat(response.outcome()).isEqualTo(outcome);
    if (code == null) {
      assertThat(response.variables()).containsEntry("sum", 5);
    } else {
      assertThat(response.error().code()).isEqualTo(code);
    }
    assertThat(callDirectories()).isEmpty();
  }

  @Test
  void writesOneExecutorPerLanguageAndAdoptsIt() throws Exception {
    final var provider = provider(Duration.ofSeconds(5));

    provider.ensureExecutors();
    provider.ensureExecutors();

    try (var entries = Files.list(directory)) {
      assertThat(entries.map(path -> path.getFileName().toString()))
          .containsExactlyInAnyOrder(
              ExecutorPackage.local(ScriptLanguage.PYTHON).functionName("local-"),
              ExecutorPackage.local(ScriptLanguage.JAVASCRIPT).functionName("local-"));
    }
  }

  @Test
  void concurrentInstancesConvergeOnOneExecutorPerLanguage() throws Exception {
    final var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(8)) {
      final var results =
          IntStream.range(0, 8)
              .mapToObj(
                  i ->
                      pool.submit(
                          () -> {
                            start.await();
                            provider(Duration.ofSeconds(5)).ensureExecutors();
                            return null;
                          }))
              .toList();
      start.countDown();
      for (var result : results) {
        result.get(10, TimeUnit.SECONDS);
      }
    }
    try (var entries = Files.list(directory)) {
      assertThat(entries).hasSize(2);
    }
  }

  @Test
  void reportsADeletedExecutorAsMissing() throws Exception {
    final var provider = provider(Duration.ofSeconds(5));
    provider.ensureExecutors();
    try (var entries = Files.list(directory)) {
      for (var entry : entries.toList()) {
        try (var files = Files.walk(entry)) {
          files.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
      }
    }

    final var response =
        provider.execute(
            ScriptLanguage.PYTHON, request("def execute(v, c):\n    return {}\n", Map.of()));

    assertThat(response.isDeploymentMissing()).isTrue();
  }

  @Test
  void reportsAMissingInterpreter() {
    final var provider =
        new LocalManagedCodeProvider(
            new LocalInterpreterDiscovery.DiscoveryReport(Map.of()),
            OBJECT_MAPPER,
            directory,
            Duration.ofSeconds(5));
    provider.ensureExecutors();

    final var response =
        provider.execute(
            ScriptLanguage.PYTHON, request("def execute(v, c):\n    return {}\n", Map.of()));

    assertThat(response.error().code()).isEqualTo("UNSUPPORTED_RUNTIME");
    assertThat(response.error().retryable()).isFalse();
  }

  private LocalManagedCodeProvider provider(Duration executionTimeout) {
    return new LocalManagedCodeProvider(INTERPRETERS, OBJECT_MAPPER, directory, executionTimeout);
  }

  private java.util.List<Path> callDirectories() {
    try (var entries = Files.list(directory)) {
      return entries.filter(path -> path.getFileName().toString().startsWith("call-")).toList();
    } catch (java.io.IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
  }

  private static ExecutionRequest request(String script, Map<String, Object> variables) {
    return new ExecutionRequest(
        "job-1", script, variables, Map.of("jobKey", "1"), Duration.ofSeconds(2).toMillis());
  }

  private static void assumeInterpreter(ScriptLanguage language) {
    assumeTrue(
        INTERPRETERS.statuses().get(language).available(),
        () -> "No usable " + language.language() + " interpreter on PATH");
  }
}
