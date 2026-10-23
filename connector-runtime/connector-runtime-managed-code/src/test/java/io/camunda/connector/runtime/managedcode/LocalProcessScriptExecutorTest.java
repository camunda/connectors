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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalProcessScriptExecutorTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final LocalInterpreterDiscovery.DiscoveryReport INTERPRETERS =
      LocalInterpreterDiscovery.discover();

  @TempDir java.nio.file.Path temporaryRoot;

  @Test
  void executesEquivalentPythonAndJavaScriptEntrypoints() throws Exception {
    requireRuntime(LocalScriptRuntime.PYTHON);
    requireRuntime(LocalScriptRuntime.JAVASCRIPT);
    final var executor = executor(Duration.ofSeconds(5));
    final var variables = Map.<String, Object>of("a", 2, "b", 3);

    final var python =
        invocation(
            "python",
            "python3",
            "sum.py",
            """
            def execute(variables, context):
                return {"sum": variables["a"] + variables["b"]}
            """);
    final var javascript =
        invocation(
            "javascript",
            "nodejs22",
            "sum.js",
            """
            export async function execute(variables, context) {
              return { sum: variables.a + variables.b };
            }
            """);

    final var pythonResult = executor.execute(python, variables, Map.of(), "python-job");
    final var javascriptResult =
        executor.execute(javascript, variables, Map.of(), "javascript-job");

    assertThat(pythonResult.response().path("outcome").asText()).isEqualTo("COMPLETED");
    assertThat(pythonResult.response().path("variables").path("sum").asInt()).isEqualTo(5);
    assertThat(javascriptResult.response().path("outcome").asText()).isEqualTo("COMPLETED");
    assertThat(javascriptResult.response().path("variables").path("sum").asInt()).isEqualTo(5);
    assertThat(pythonResult.mode()).isEqualTo(LocalProcessScriptExecutor.MODE);
    assertThat(temporaryRoot).isEmptyDirectory();
  }

  @Test
  void normalizesScriptFailure() throws Exception {
    requireRuntime(LocalScriptRuntime.JAVASCRIPT);
    final var invocation =
        invocation(
            "javascript",
            "nodejs22",
            "failure.js",
            """
            export function execute() {
              throw new Error("expected failure");
            }
            """);

    final var result =
        executor(Duration.ofSeconds(5)).execute(invocation, Map.of(), Map.of(), "job");

    assertThat(result.response().path("outcome").asText()).isEqualTo("FAILED");
    assertThat(result.response().path("error").path("code").asText()).isEqualTo("SCRIPT_ERROR");
    assertThat(result.response().path("error").path("message").asText())
        .isEqualTo("expected failure");
    assertThat(temporaryRoot).isEmptyDirectory();
  }

  @Test
  void boundsOutputAndTimesOutExecution() throws Exception {
    requireRuntime(LocalScriptRuntime.JAVASCRIPT);
    final var outputInvocation =
        invocation(
            "javascript",
            "nodejs22",
            "large-output.js",
            """
            export function execute() {
              return { value: "x".repeat(1000) };
            }
            """);
    final var outputExecutor =
        new LocalProcessScriptExecutor(
            OBJECT_MAPPER,
            INTERPRETERS,
            new LocalProcessScriptExecutor.Limits(
                64 * 1_024, 64 * 1_024, 256, 4 * 1_024, Duration.ofSeconds(5)),
            temporaryRoot);

    final var outputResult =
        outputExecutor.execute(outputInvocation, Map.of(), Map.of(), "large-output");

    assertThat(outputResult.response().path("error").path("code").asText())
        .isEqualTo("RESOURCE_LIMIT");
    assertThat(temporaryRoot).isEmptyDirectory();

    final var timeoutInvocation =
        invocation(
            "javascript",
            "nodejs22",
            "timeout.js",
            """
            export async function execute() {
              await new Promise((resolve) => setTimeout(resolve, 60_000));
              return {};
            }
            """);

    final var timeoutResult =
        executor(Duration.ofMillis(500)).execute(timeoutInvocation, Map.of(), Map.of(), "timeout");

    assertThat(timeoutResult.response().path("error").path("code").asText()).isEqualTo("TIMEOUT");
    assertThat(temporaryRoot).isEmptyDirectory();
  }

  private LocalProcessScriptExecutor executor(Duration timeout) {
    return new LocalProcessScriptExecutor(
        OBJECT_MAPPER,
        INTERPRETERS,
        new LocalProcessScriptExecutor.Limits(
            64 * 1_024, 64 * 1_024, 64 * 1_024, 4 * 1_024, timeout),
        temporaryRoot);
  }

  private static ManagedScriptInvocation invocation(
      String language, String runtime, String resourceName, String source) {
    return new ManagedScriptInvocation(
        ManagedScriptInvocation.Status.READY,
        "fake",
        "fake-deployment",
        language,
        runtime,
        1L,
        resourceName,
        source.getBytes(StandardCharsets.UTF_8));
  }

  private static void requireRuntime(LocalScriptRuntime runtime) {
    final var status = INTERPRETERS.statuses().get(runtime);
    assumeTrue(status.available(), status.diagnostic());
  }
}
