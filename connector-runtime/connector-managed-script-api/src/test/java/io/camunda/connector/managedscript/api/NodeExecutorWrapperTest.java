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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs the JavaScript wrapper on a real {@code node}, through its local entry point. Skipped when
 * no {@code node} is on the PATH. The local mode only kills the process group of its own script, so
 * the tests never touch other processes of the host.
 */
class NodeExecutorWrapperTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @TempDir static Path wrapper;
  @TempDir Path callTmp;

  private static boolean nodeAvailable;

  @BeforeAll
  static void installWrapper() throws IOException {
    nodeAvailable = nodeAvailable();
    for (var file : ExecutorWrapper.of(ScriptLanguage.JAVASCRIPT).files().entrySet()) {
      Files.write(wrapper.resolve(file.getKey()), file.getValue());
    }
  }

  @BeforeEach
  void requireNode() {
    assumeTrue(nodeAvailable, "no node on the PATH: the JavaScript wrapper tests need Node.js 20+");
  }

  @Test
  void returnsTheObjectOfExecute() throws Exception {
    final var response =
        call(
            "export function execute(variables, context) {"
                + " return { sum: variables.a + variables.b, job: context.jobKey }; }",
            Map.of("a", 2, "b", 3));

    assertThat(response.get("contractVersion").asText()).isEqualTo("1");
    assertThat(response.get("outcome").asText()).isEqualTo("COMPLETED");
    assertThat(response.get("variables").get("sum").asInt()).isEqualTo(5);
    assertThat(response.get("variables").get("job").asText()).isEqualTo("42");
  }

  @Test
  void awaitsAnAsyncExecute() throws Exception {
    final var response =
        call("export async function execute() { return { done: await Promise.resolve(true) }; }");

    assertThat(response.get("outcome").asText()).isEqualTo("COMPLETED");
    assertThat(response.get("variables").get("done").asBoolean()).isTrue();
  }

  @Test
  void reportsASyntaxErrorAsAScriptError() throws Exception {
    final var error = errorOf(call("export function execute( { return {}; }"));

    assertThat(error.get("code").asText()).isEqualTo("SCRIPT_ERROR");
    assertThat(error.get("message").asText()).startsWith("SyntaxError");
  }

  @Test
  void reportsAnExceptionWithItsStackTrace() throws Exception {
    final var error =
        errorOf(call("export function execute() { throw new RangeError('too far'); }"));

    assertThat(error.get("code").asText()).isEqualTo("SCRIPT_ERROR");
    assertThat(error.get("message").asText()).isEqualTo("RangeError: too far");
    assertThat(error.get("stackTrace").asText()).contains("user_script.mjs");
    assertThat(error.get("retryable").asBoolean()).isFalse();
  }

  @Test
  void rejectsAResultThatIsNotAnObject() throws Exception {
    final var error = errorOf(call("export function execute() { return [1, 2]; }"));

    assertThat(error.get("code").asText()).isEqualTo("INVALID_RESULT");
  }

  @Test
  void rejectsAScriptThatWritesToStandardOutput() throws Exception {
    final var error =
        errorOf(
            call("export function execute() { process.stdout.write('hello'); return { a: 1 }; }"));

    assertThat(error.get("code").asText()).isEqualTo("INVALID_RESULT");
  }

  @Test
  void doesNotLetAScriptForgeAPlatformFailure() throws Exception {
    final var error =
        errorOf(
            call(
                "export function execute() {"
                    + " process.stdout.write(JSON.stringify({ contractVersion: '1', outcome: 'FAILED',"
                    + " error: { code: 'PLATFORM_THROTTLED', message: 'retry me', retryable: true } }));"
                    + " process.exit(0); }"));

    assertThat(error.get("code").asText()).isEqualTo("INVALID_RESULT");
    assertThat(error.get("retryable").asBoolean()).isFalse();
  }

  @Test
  void sendsConsoleOutputToTheLogsNotTheResponse() throws Exception {
    final var response =
        call("export function execute() { console.log('to the logs'); return { a: 1 }; }");

    assertThat(response.get("outcome").asText()).isEqualTo("COMPLETED");
    assertThat(response.toString()).doesNotContain("to the logs");
  }

  @Test
  void sendsEveryConsoleMethodToTheLogs() throws Exception {
    final var response =
        call(
            "export function execute() {"
                + " console.debug('d'); console.dir({ a: 1 }); console.table([{ a: 1 }]);"
                + " return { a: 1 }; }");

    assertThat(response.get("outcome").asText()).isEqualTo("COMPLETED");
  }

  @Test
  void keepsMultibyteCharactersSplitAcrossStdinChunks() throws Exception {
    final var text = "é€😀".repeat(100_000);
    final var response =
        call(
            "export function execute(variables) { return { text: variables.text }; }",
            Map.of("text", text));

    assertThat(response.get("outcome").asText()).isEqualTo("COMPLETED");
    assertThat(response.get("variables").get("text").asText()).isEqualTo(text);
  }

  @Test
  void limitsTheStackTraceOfAForgedScriptError() throws Exception {
    final var error =
        errorOf(
            call(
                "export function execute() {"
                    + " process.stdout.write(JSON.stringify({ contractVersion: '1', outcome: 'FAILED',"
                    + " error: { code: 'SCRIPT_ERROR', message: 'm', stackTrace: 'x'.repeat(100000) } }));"
                    + " process.exit(0); }"));

    assertThat(error.get("code").asText()).isEqualTo("SCRIPT_ERROR");
    assertThat(error.get("stackTrace").asText()).hasSize(8192);
    assertThat(error.get("truncated").asBoolean()).isTrue();
  }

  @Test
  void returnsWhenAScriptReplacesItsOutputFilesWithAFifo() throws Exception {
    final var response =
        call(
            "import { execSync } from 'node:child_process'; import { unlinkSync } from 'node:fs';"
                + " export function execute() {"
                + " unlinkSync('.stderr'); execSync('mkfifo .stderr'); return { a: 1 }; }");

    assertThat(response.get("outcome").asText()).isEqualTo("COMPLETED");
  }

  @Test
  void reportsAScriptThatLogsBeforeRunningOutOfMemoryAsAResourceLimit() throws Exception {
    final var request =
        request(
            "export function execute() {"
                + " console.log('x'.repeat(10000)); const kept = [];"
                + " while (true) kept.push(new Array(1000000).fill(kept.length)); }",
            Map.of());
    request.put("timeoutMs", 25_000);

    assertThat(errorOf(call(request)).get("code").asText()).isEqualTo("RESOURCE_LIMIT");
  }

  @Test
  void reportsATimerThatThrowsWhileExecuteIsAwaitingAsAScriptError() throws Exception {
    final var error =
        errorOf(
            call(
                "export async function execute() {"
                    + " setTimeout(() => { throw new Error('late boom'); }, 10);"
                    + " await new Promise((resolve) => setTimeout(resolve, 300)); return { a: 1 }; }"));

    assertThat(error.get("code").asText()).isEqualTo("SCRIPT_ERROR");
    assertThat(error.get("message").asText()).contains("late boom");
  }

  @Test
  void reportsAnUnhandledRejectionAsAScriptError() throws Exception {
    final var error =
        errorOf(
            call(
                "export async function execute() {"
                    + " Promise.reject(new Error('nobody waits'));"
                    + " await new Promise((resolve) => setTimeout(resolve, 300)); return { a: 1 }; }"));

    assertThat(error.get("code").asText()).isEqualTo("SCRIPT_ERROR");
    assertThat(error.get("message").asText()).contains("nobody waits");
  }

  @Test
  void reportsAnExitBeforeExecuteReturnsAsAScriptError() throws Exception {
    final var error = errorOf(call("export function execute() { process.exit(1); }"));

    assertThat(error.get("code").asText()).isEqualTo("SCRIPT_ERROR");
    assertThat(error.get("message").asText()).contains("exit code 1");
  }

  @Test
  void completesAScriptThatLeavesATimerRunning() throws Exception {
    final var request =
        request(
            "export function execute() { setInterval(() => {}, 1000); return { a: 1 }; }",
            Map.of());
    request.put("timeoutMs", 2_000);

    assertThat(call(request).get("outcome").asText()).isEqualTo("COMPLETED");
  }

  @Test
  void stopsAScriptAtItsTimeout() throws Exception {
    final var request = request("export function execute() { while (true) {} }", Map.of());
    request.put("timeoutMs", 500);

    final var error = errorOf(call(request));

    assertThat(error.get("code").asText()).isEqualTo("TIMEOUT");
  }

  @Test
  void limitsTheSizeOfTheResponse() throws Exception {
    final var error =
        errorOf(call("export function execute() { return { big: 'x'.repeat(2_000_000) }; }"));

    assertThat(error.get("code").asText()).isEqualTo("RESOURCE_LIMIT");
  }

  @Test
  void givesTheScriptNoEnvironmentButPathHomeTmpdirAndLang() throws Exception {
    // The wrapper runs with AWS_SECRET_ACCESS_KEY set; macOS adds __CF_USER_TEXT_ENCODING to every
    // process.
    final var response =
        call(
            "export function execute() { return { names: Object.keys(process.env)"
                + ".filter((name) => !name.startsWith('__CF_')).sort() }; }");

    assertThat(response.get("variables").get("names").toString())
        .isEqualTo("[\"HOME\",\"LANG\",\"PATH\",\"TMPDIR\"]");
  }

  @Test
  void rejectsAnotherContractVersionWithoutRunningTheScript() throws Exception {
    final var request =
        request("export function execute() { throw new Error('must not run'); }", Map.of());
    request.put("contractVersion", "2");

    final var error = errorOf(call(request));

    assertThat(error.get("code").asText()).isEqualTo("UNSUPPORTED_CONTRACT_VERSION");
  }

  @Test
  void rejectsVariablesThatAreNotAnObject() throws Exception {
    final var request = request("export function execute() { return {}; }", Map.of());
    request.put("variables", "not an object");

    assertThat(errorOf(call(request)).get("code").asText()).isEqualTo("INVALID_INPUT");
  }

  @Test
  void leavesNoCallDirectoryBehind() throws Exception {
    call("export function execute() { return { a: 1 }; }");
    call("export function execute() { throw new Error('boom'); }");
    final var request = request("export function execute() { while (true) {} }", Map.of());
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
    final var stdout = Files.createTempFile(callTmp, "response", ".json");
    final var builder =
        new ProcessBuilder("node", wrapper.resolve("local_call.mjs").toString())
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .redirectOutput(stdout.toFile());
    builder.environment().put("EXECUTOR_TMP", callTmp.toString());
    builder.environment().put("AWS_SECRET_ACCESS_KEY", "not-for-scripts");
    final var process = builder.start();
    try (var stdin = process.getOutputStream()) {
      stdin.write(JSON.writeValueAsBytes(request));
    }
    try {
      assertThat(process.waitFor(30, TimeUnit.SECONDS)).as("the wrapper returned in time").isTrue();
      return JSON.readTree(Files.readAllBytes(stdout));
    } finally {
      process.destroyForcibly();
      Files.deleteIfExists(stdout);
    }
  }

  private static JsonNode errorOf(JsonNode response) {
    assertThat(response.get("outcome").asText()).isEqualTo("FAILED");
    return response.get("error");
  }

  private static boolean nodeAvailable() {
    try {
      final var process =
          new ProcessBuilder("node", "--version")
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
