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
package io.camunda.connector.managedscript.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs the Python wrapper on a real {@code python3}, through its local entry point. Skipped when no
 * {@code python3} 3.10 or later is on the PATH. The local mode only kills the process group of its
 * own script, so the tests never touch other processes of the host.
 */
class PythonExecutorWrapperTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @TempDir static Path wrapper;
  @TempDir Path callTmp;

  private static Optional<Path> python;

  @BeforeAll
  static void installWrapper() throws IOException {
    python = findPython().filter(PythonExecutorWrapperTest::supported);
    for (var file : ExecutorWrapper.of(ScriptLanguage.PYTHON).files().entrySet()) {
      Files.write(wrapper.resolve(file.getKey()), file.getValue());
    }
  }

  @BeforeEach
  void requirePython() {
    assumeTrue(
        python.isPresent(),
        "no python3 3.10 or later on the PATH: the Python wrapper tests need Python 3.10+");
  }

  @Test
  void returnsTheDictOfExecute() throws Exception {
    final var response =
        call(
            """
            def execute(variables, context):
                return {"sum": variables["a"] + variables["b"], "job": context["jobKey"]}
            """,
            Map.of("a", 2, "b", 3));

    assertThat(response.get("contractVersion").asText()).isEqualTo("1");
    assertThat(response.get("outcome").asText()).isEqualTo("COMPLETED");
    assertThat(response.get("variables").get("sum").asInt()).isEqualTo(5);
    assertThat(response.get("variables").get("job").asText()).isEqualTo("42");
  }

  @Test
  void reportsASyntaxErrorAsAScriptError() throws Exception {
    final var error = errorOf(call("def execute(:\n    return {}\n"));

    assertThat(error.get("code").asText()).isEqualTo("SCRIPT_ERROR");
    assertThat(error.get("message").asText()).startsWith("SyntaxError");
  }

  @Test
  void reportsAScriptWithoutExecuteAsAScriptError() throws Exception {
    final var error = errorOf(call("x = 1\n"));

    assertThat(error.get("code").asText()).isEqualTo("SCRIPT_ERROR");
    assertThat(error.get("message").asText()).contains("execute(variables, context)");
  }

  @Test
  void reportsAnExceptionWithItsStackTrace() throws Exception {
    final var error =
        errorOf(call("def execute(variables, context):\n    raise ValueError('too far')\n"));

    assertThat(error.get("code").asText()).isEqualTo("SCRIPT_ERROR");
    assertThat(error.get("message").asText()).isEqualTo("ValueError: too far");
    assertThat(error.get("stackTrace").asText()).contains("user_script.py");
    assertThat(error.get("retryable").asBoolean()).isFalse();
    assertThat(error.get("truncated").asBoolean()).isFalse();
  }

  @Test
  void keepsTheEndOfALongTraceback() throws Exception {
    final var error =
        errorOf(
            call(
                "def execute(variables, context):\n"
                    + "    raise ValueError('x' * 10000 + 'nearest the error')\n"));

    assertThat(error.get("message").asText()).hasSize(1024);
    assertThat(error.get("stackTrace").asText()).hasSize(8192).endsWith("nearest the error\n");
    assertThat(error.get("truncated").asBoolean()).isTrue();
  }

  @Test
  void runsADataclassWithPostponedAnnotations() throws Exception {
    final var response =
        call(
            """
            from __future__ import annotations
            from dataclasses import asdict, dataclass

            @dataclass
            class Point:
                x: int

            def execute(variables, context):
                return asdict(Point(1))
            """);

    assertThat(response.get("outcome").asText()).isEqualTo("COMPLETED");
    assertThat(response.get("variables").get("x").asInt()).isEqualTo(1);
  }

  @Test
  void rejectsAResultThatIsNotADict() throws Exception {
    final var error = errorOf(call("def execute(variables, context):\n    return [1, 2]\n"));

    assertThat(error.get("code").asText()).isEqualTo("INVALID_RESULT");
  }

  @Test
  void rejectsAResultThatIsNotJson() throws Exception {
    assertThat(
            errorOf(call("def execute(variables, context):\n    return {'a': object()}\n"))
                .get("code")
                .asText())
        .isEqualTo("INVALID_RESULT");
    assertThat(
            errorOf(call("def execute(variables, context):\n    return {'a': float('nan')}\n"))
                .get("code")
                .asText())
        .isEqualTo("INVALID_RESULT");
  }

  @Test
  void rejectsAScriptThatWritesToStandardOutput() throws Exception {
    final var error =
        errorOf(
            call(
                """
                import os

                def execute(variables, context):
                    os.write(1, b"hello")
                    return {"a": 1}
                """));

    assertThat(error.get("code").asText()).isEqualTo("INVALID_RESULT");
  }

  @Test
  void doesNotLetAScriptForgeAPlatformFailure() throws Exception {
    final var error =
        errorOf(
            call(
                """
                import json, os

                def execute(variables, context):
                    os.write(1, json.dumps({"contractVersion": "1", "outcome": "FAILED",
                        "error": {"code": "PLATFORM_THROTTLED", "message": "retry me",
                        "retryable": True}}).encode())
                    os._exit(0)
                """));

    assertThat(error.get("code").asText()).isEqualTo("INVALID_RESULT");
    assertThat(error.get("retryable").asBoolean()).isFalse();
  }

  @Test
  void rejectsAForgedResponseThatIsNotJson() throws Exception {
    for (var number : new String[] {"NaN", "Infinity", "1e999"}) {
      final var error =
          errorOf(
              call(
                  "import os\n\ndef execute(variables, context):\n"
                      + "    os.write(1, b'{\"contractVersion\": \"1\", \"outcome\": \"COMPLETED\","
                      + " \"variables\": {\"a\": "
                      + number
                      + "}}')\n"
                      + "    os._exit(0)\n"));

      assertThat(error.get("code").asText()).as(number).isEqualTo("INVALID_RESULT");
    }
  }

  @Test
  void isNotBlockedByAScriptThatReplacesItsOutputWithAFifo() throws Exception {
    final var response =
        call(
            """
            import os

            def execute(variables, context):
                for name in (".stdout", ".stderr"):
                    os.unlink(name)
                    os.mkfifo(name)
                return {"a": 1}
            """);

    assertThat(response.get("outcome").asText()).isEqualTo("COMPLETED");
  }

  @Test
  void keepsTheResponseCleanWhileAThreadKeepsPrinting() throws Exception {
    final var response =
        call(
            """
            import threading, time

            def spam():
                while True:
                    print("late output")

            def execute(variables, context):
                threading.Thread(target=spam, daemon=True).start()
                time.sleep(0.2)
                return {"a": 1}
            """);

    assertThat(response.get("outcome").asText()).isEqualTo("COMPLETED");
  }

  @Test
  void doesNotLetAScriptForgeAnOversizedStackTrace() throws Exception {
    final var error =
        errorOf(
            call(
                """
                import json, os

                def execute(variables, context):
                    trace = "x" * 20000 + "nearest the error"
                    os.write(1, json.dumps({"contractVersion": "1", "outcome": "FAILED",
                        "error": {"code": "SCRIPT_ERROR", "message": "m", "stackTrace": trace,
                        "truncated": False}}).encode())
                    os._exit(0)
                """));

    assertThat(error.get("code").asText()).isEqualTo("SCRIPT_ERROR");
    assertThat(error.get("stackTrace").asText()).hasSize(8192).endsWith("nearest the error");
    assertThat(error.get("truncated").asBoolean()).isTrue();
  }

  @Test
  void rejectsAResultWhoseSerializationRaises() throws Exception {
    final var error =
        errorOf(
            call(
                """
                class Broken(dict):
                    def items(self):
                        raise RuntimeError("no items")

                def execute(variables, context):
                    return {"a": Broken(k=1)}
                """));

    assertThat(error.get("code").asText()).isEqualTo("INVALID_RESULT");
    assertThat(error.get("message").asText()).contains("no items");
  }

  @Test
  void rejectsAResultWhoseSerializationExits() throws Exception {
    final var error =
        errorOf(
            call(
                """
                class Exiting(dict):
                    def items(self):
                        raise SystemExit(3)

                def execute(variables, context):
                    return {"a": Exiting(k=1)}
                """));

    assertThat(error.get("code").asText()).isEqualTo("INVALID_RESULT");
  }

  @Test
  void keepsTheResourceLimitWhenMemoryRunsOutWhileSerializingTheResult() throws Exception {
    final var error =
        errorOf(
            call(
                """
                class Exhausted(dict):
                    def items(self):
                        raise MemoryError()

                def execute(variables, context):
                    print("x" * 5000)
                    return {"a": Exhausted(k=1)}
                """));

    assertThat(error.get("code").asText()).isEqualTo("RESOURCE_LIMIT");
  }

  @Test
  void sendsPrintToTheLogsNotTheResponse() throws Exception {
    final var response =
        call("def execute(variables, context):\n    print('to the logs')\n    return {'a': 1}\n");

    assertThat(response.get("outcome").asText()).isEqualTo("COMPLETED");
    assertThat(response.toString()).doesNotContain("to the logs");
  }

  @Test
  void completesAScriptThatLeavesAThreadRunning() throws Exception {
    final var request =
        request(
            """
            import threading, time

            def execute(variables, context):
                threading.Thread(target=time.sleep, args=(60,)).start()
                return {"a": 1}
            """,
            Map.of());
    request.put("timeoutMs", 2_000);

    assertThat(call(request).get("outcome").asText()).isEqualTo("COMPLETED");
  }

  @Test
  void stopsAScriptAtItsTimeout() throws Exception {
    final var request =
        request("def execute(variables, context):\n    while True:\n        pass\n", Map.of());
    request.put("timeoutMs", 500);

    final var error = errorOf(call(request));

    assertThat(error.get("code").asText()).isEqualTo("TIMEOUT");
  }

  @Test
  void limitsTheSizeOfTheResponse() throws Exception {
    final var error =
        errorOf(call("def execute(variables, context):\n    return {'big': 'x' * 2_000_000}\n"));

    assertThat(error.get("code").asText()).isEqualTo("RESOURCE_LIMIT");
  }

  // RLIMIT_AS is not enforced on macOS.
  @Test
  @EnabledOnOs(OS.LINUX)
  void limitsTheMemoryOfTheScript() throws Exception {
    final var error =
        errorOf(
            call(
                "def execute(variables, context):\n"
                    + "    return {'size': len(bytearray(512 * 1024 * 1024))}\n"));

    assertThat(error.get("code").asText()).isEqualTo("RESOURCE_LIMIT");
  }

  @Test
  void givesTheScriptNoEnvironmentButPathHomeTmpdirAndLang() throws Exception {
    // The wrapper runs with AWS_SECRET_ACCESS_KEY set; macOS adds __CF_USER_TEXT_ENCODING to every
    // process.
    final var response =
        call(
            """
            import os

            def execute(variables, context):
                return {"names": sorted(name for name in os.environ if not name.startswith("__CF_"))}
            """);

    assertThat(response.get("variables").get("names").toString())
        .isEqualTo("[\"HOME\",\"LANG\",\"PATH\",\"TMPDIR\"]");
  }

  @Test
  void rejectsAnotherContractVersionWithoutRunningTheScript() throws Exception {
    final var request =
        request(
            "def execute(variables, context):\n    raise RuntimeError('must not run')\n", Map.of());
    request.put("contractVersion", "2");

    final var error = errorOf(call(request));

    assertThat(error.get("code").asText()).isEqualTo("UNSUPPORTED_CONTRACT_VERSION");
  }

  @Test
  void rejectsVariablesThatAreNotAnObject() throws Exception {
    final var request = request("def execute(variables, context):\n    return {}\n", Map.of());
    request.put("variables", "not an object");

    assertThat(errorOf(call(request)).get("code").asText()).isEqualTo("INVALID_INPUT");
  }

  @Test
  void removesADirectoryTheScriptMadeUnwritable() throws Exception {
    call(
        """
        import os

        def execute(variables, context):
            os.mkdir("locked")
            open("locked/file", "w").close()
            os.chmod("locked", 0o500)
            return {"a": 1}
        """);

    try (var left = Files.list(callTmp)) {
      assertThat(left).isEmpty();
    }
  }

  @Test
  void leavesNoCallDirectoryBehind() throws Exception {
    call("def execute(variables, context):\n    return {'a': 1}\n");
    call("def execute(variables, context):\n    raise RuntimeError('boom')\n");
    final var request =
        request("def execute(variables, context):\n    while True:\n        pass\n", Map.of());
    request.put("timeoutMs", 200);
    call(request);

    try (var left = Files.list(callTmp)) {
      assertThat(left).isEmpty();
    }
  }

  private JsonNode call(String script) throws Exception {
    return call(request(script, Map.of()));
  }

  private JsonNode call(String script, Map<String, Object> variables) throws Exception {
    return call(request(script, variables));
  }

  private static Map<String, Object> request(String script, Map<String, Object> variables) {
    final Map<String, Object> request = new HashMap<>();
    request.put("contractVersion", ExecutionRequest.CONTRACT_VERSION);
    request.put("executionId", "42");
    request.put("script", script);
    request.put("variables", variables);
    request.put("context", Map.of("jobKey", "42"));
    request.put("timeoutMs", 10_000);
    return request;
  }

  private JsonNode call(Map<String, Object> request) throws Exception {
    final var builder =
        new ProcessBuilder(
                python.orElseThrow().toString(), "-I", wrapper.resolve("local_call.py").toString())
            .redirectError(ProcessBuilder.Redirect.DISCARD);
    builder.environment().put("EXECUTOR_TMP", callTmp.toString());
    builder.environment().put("AWS_SECRET_ACCESS_KEY", "not-for-scripts");
    // To a file, so that the deadline applies before anything is read: reading the pipe to its end
    // would wait for a hung wrapper as long as the wrapper does.
    final var output = Files.createTempFile(wrapper, "response", ".json");
    builder.redirectOutput(output.toFile());
    final var process = builder.start();
    try (var stdin = process.getOutputStream()) {
      stdin.write(JSON.writeValueAsBytes(request));
    }
    if (!process.waitFor(30, TimeUnit.SECONDS)) {
      process.descendants().forEach(ProcessHandle::destroyForcibly);
      process.destroyForcibly();
      throw new AssertionError("the wrapper did not answer within 30 seconds");
    }
    return JSON.readTree(Files.readAllBytes(output));
  }

  private static JsonNode errorOf(JsonNode response) {
    assertThat(response.get("outcome").asText()).isEqualTo("FAILED");
    return response.get("error");
  }

  private static Optional<Path> findPython() {
    return Arrays.stream(System.getenv().getOrDefault("PATH", "").split(File.pathSeparator))
        .filter(directory -> !directory.isEmpty())
        .map(directory -> Path.of(directory, "python3").toAbsolutePath())
        .filter(Files::isExecutable)
        .findFirst();
  }

  private static boolean supported(Path python) {
    try {
      final var process =
          new ProcessBuilder(
                  python.toString(),
                  "-c",
                  "import sys; sys.exit(0 if sys.version_info >= (3, 10) else 1)")
              .redirectErrorStream(true)
              .redirectOutput(ProcessBuilder.Redirect.DISCARD)
              .start();
      return process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0;
    } catch (IOException e) {
      return false;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }
}
