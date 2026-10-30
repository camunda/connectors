/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.managedscript.local;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.managedscript.api.ErrorCode;
import io.camunda.connector.managedscript.api.ExecutionRequest;
import io.camunda.connector.managedscript.api.ExecutionResponse;
import io.camunda.connector.managedscript.api.ExecutorWrapper;
import io.camunda.connector.managedscript.api.ProvisioningException;
import io.camunda.connector.managedscript.api.ScriptLanguage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

/**
 * Runs the local provider on the interpreters of the host: a test needing one that is not installed
 * is skipped. The shims are shell scripts, so these tests do not run on Windows, which the provider
 * does not support.
 */
class LocalManagedCodeProviderTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  private static Path node;
  private static Path python;

  @TempDir Path directory;
  @TempDir Path bin;

  @BeforeAll
  static void findInterpreters(@TempDir Path scratch) {
    node = realInterpreter(LocalRuntime.NODE, scratch);
    python = realInterpreter(LocalRuntime.PYTHON, scratch);
  }

  @Test
  void warnsThatItIsNotASandbox() {
    final var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    final var logger = (Logger) LoggerFactory.getLogger(LocalManagedCodeProvider.class);
    logger.addAppender(appender);
    try {
      provider(List.of());
    } finally {
      logger.detachAppender(appender);
    }

    assertThat(appender.list)
        .anySatisfy(
            event -> {
              assertThat(event.getLevel()).isEqualTo(Level.WARN);
              assertThat(event.getFormattedMessage())
                  .contains("trusted development")
                  .contains("not a sandbox");
            });
  }

  @Test
  void hasTheNameOfItsProperty() {
    assertThat(provider(List.of()).name()).isEqualTo("local");
  }

  @Test
  void runsANodeScriptThroughTheWrapper() throws IOException {
    requireNode();
    final var provider = provider(List.of(link("node", node)));
    provider.ensureExecutors();

    final var response =
        provider.execute(
            ScriptLanguage.JAVASCRIPT,
            request(
                "export function execute(variables, context) {"
                    + " return { sum: variables.a + variables.b, job: context.jobKey }; }",
                Map.of("a", 2, "b", 3),
                10_000));

    assertThat(response.outcome()).isEqualTo(ExecutionResponse.COMPLETED);
    assertThat(response.variables()).containsEntry("sum", 5).containsEntry("job", "42");
  }

  @Test
  void runsAPythonScriptThroughTheWrapper() throws IOException {
    requirePython();
    final var provider = provider(List.of(link("python3", python)));
    provider.ensureExecutors();

    final var response =
        provider.execute(
            ScriptLanguage.PYTHON,
            request(
                "def execute(variables, context):\n    return {'sum': variables['a'] + variables['b']}\n",
                Map.of("a", 2, "b", 3),
                10_000));

    assertThat(response.outcome()).isEqualTo(ExecutionResponse.COMPLETED);
    assertThat(response.variables()).containsEntry("sum", 5);
  }

  @Test
  void returnsAScriptErrorWithItsStackTrace() throws IOException {
    requireNode();
    final var provider = provider(List.of(link("node", node)));
    provider.ensureExecutors();

    final var response =
        provider.execute(
            ScriptLanguage.JAVASCRIPT,
            request(
                "export function execute() { throw new RangeError('too far'); }",
                Map.of(),
                10_000));

    assertThat(response.outcome()).isEqualTo(ExecutionResponse.FAILED);
    assertThat(response.error().code()).isEqualTo("SCRIPT_ERROR");
    assertThat(response.error().message()).isEqualTo("RangeError: too far");
    assertThat(response.error().stackTrace()).isNotBlank();
  }

  @Test
  void stopsAScriptAtItsTimeout() throws IOException {
    requireNode();
    final var provider = provider(List.of(link("node", node)));
    provider.ensureExecutors();

    final var response =
        provider.execute(
            ScriptLanguage.JAVASCRIPT,
            request("export function execute() { while (true) {} }", Map.of(), 500));

    assertThat(response.error().code()).isEqualTo("TIMEOUT");
  }

  @Test
  void writesTheWrapperFilesUnderTheNameOfTheirDigest() throws IOException {
    requireNode();
    final var provider = provider(List.of(link("node", node)));

    provider.ensureExecutors();

    final var wrapper = wrapperOf(ScriptLanguage.JAVASCRIPT);
    final var installed = directory.resolve(wrapper.executorName("", "local"));
    assertThat(installed.getFileName().toString()).matches("js-[0-9a-f]{12}");
    try (Stream<Path> files = Files.list(installed)) {
      assertThat(files.map(file -> file.getFileName().toString()))
          .containsExactlyInAnyOrder("child.mjs", "executor_core.mjs", "local_call.mjs");
    }
    assertThat(Files.readAllBytes(installed.resolve("executor_core.mjs")))
        .isEqualTo(wrapper.files().get("executor_core.mjs"));
    assertThat(Files.getPosixFilePermissions(directory))
        .isEqualTo(PosixFilePermissions.fromString("rwx------"));
  }

  @Test
  void canBeCalledAgainOnceTheFilesAreThere() throws IOException {
    requireNode();
    final var provider = provider(List.of(link("node", node)));

    provider.ensureExecutors();
    provider.ensureExecutors();

    assertThat(provider.interpreter(ScriptLanguage.JAVASCRIPT)).isNotNull();
  }

  @Test
  void refusesWrapperFilesThatDoNotMatchTheirDigest() throws IOException {
    requireNode();
    final var provider = provider(List.of(link("node", node)));
    provider.ensureExecutors();
    final var installed =
        directory.resolve(wrapperOf(ScriptLanguage.JAVASCRIPT).executorName("", "local"));
    Files.writeString(installed.resolve("executor_core.mjs"), "// changed");

    final var second = provider(List.of(link("node", node)));

    assertThatThrownBy(second::ensureExecutors)
        .isInstanceOfSatisfying(
            ProvisioningException.class,
            e -> {
              assertThat(e.code()).isEqualTo(ErrorCode.PLATFORM_REJECTED);
              assertThat(e.getMessage())
                  .contains("do not match the wrapper")
                  .contains(installed.toString());
            });
  }

  @Test
  void refusesAFileThatTheWrapperDoesNotHave() throws IOException {
    requireNode();
    final var provider = provider(List.of(link("node", node)));
    provider.ensureExecutors();
    final var installed =
        directory.resolve(wrapperOf(ScriptLanguage.JAVASCRIPT).executorName("", "local"));
    Files.writeString(installed.resolve("extra.mjs"), "// planted");

    assertThatThrownBy(() -> provider(List.of(link("node", node))).ensureExecutors())
        .isInstanceOf(ProvisioningException.class)
        .hasMessageContaining("do not match the wrapper");
  }

  @Test
  void reportsAMissingExecutorUntilTheFilesAreWrittenAgain() throws IOException {
    requireNode();
    final var provider = provider(List.of(link("node", node)));
    final var script = request("export function execute() { return { a: 1 }; }", Map.of(), 10_000);
    assertThat(provider.execute(ScriptLanguage.JAVASCRIPT, script).error().code())
        .isEqualTo("DEPLOYMENT_MISSING");
    provider.ensureExecutors();
    assertThat(provider.execute(ScriptLanguage.JAVASCRIPT, script).outcome())
        .isEqualTo(ExecutionResponse.COMPLETED);

    deleteRecursively(
        directory.resolve(wrapperOf(ScriptLanguage.JAVASCRIPT).executorName("", "local")));

    final var missing = provider.execute(ScriptLanguage.JAVASCRIPT, script);
    assertThat(missing.isDeploymentMissing()).isTrue();
    provider.ensureExecutors();
    assertThat(provider.execute(ScriptLanguage.JAVASCRIPT, script).outcome())
        .isEqualTo(ExecutionResponse.COMPLETED);
  }

  @Test
  void failsWithAClearMessageWhenNoInterpreterIsInstalled() {
    final var provider = provider(List.of(bin));

    assertThatThrownBy(provider::ensureExecutors)
        .isInstanceOfSatisfying(
            ProvisioningException.class,
            e -> {
              assertThat(e.code()).isEqualTo(ErrorCode.PLATFORM_REJECTED);
              assertThat(e.getMessage())
                  .contains("Node.js 20 or later was not found on the PATH")
                  .contains("Python 3.10 or later was not found on the PATH");
            });
  }

  @Test
  void saysWhyAShimCannotPickAVersion() throws IOException {
    shim("node", "echo 'No version is set for command node' >&2\nexit 126");
    shim("python3", "echo 'pyenv: python3: command not found' >&2\nexit 127");

    assertThatThrownBy(provider(List.of(bin))::ensureExecutors)
        .isInstanceOf(ProvisioningException.class)
        .hasMessageContaining(bin.resolve("node") + ": No version is set for command node")
        .hasMessageContaining("pyenv: python3: command not found");
  }

  @Test
  void rejectsAnInterpreterThatIsTooOld() throws IOException {
    shim("node", "case \"$1\" in -p) printf '%s\\n18.19.0\\n' \"$0\";; esac");
    shim("python3", "printf '%s\\n3.9\\n' \"$0\"");

    assertThatThrownBy(provider(List.of(bin))::ensureExecutors)
        .isInstanceOf(ProvisioningException.class)
        .hasMessageContaining("version 18.19.0 is older than Node.js 20")
        .hasMessageContaining("version 3.9 is older than Python 3.10");
  }

  @Test
  void runsWithTheRealInterpreterBehindAShim() throws IOException {
    requireNode();
    shim("node", "exec '" + node + "' \"$@\"");
    final var provider = provider(List.of(bin));

    provider.ensureExecutors();

    assertThat(provider.interpreter(ScriptLanguage.JAVASCRIPT)).isNotEqualTo(bin.resolve("node"));
    assertThat(provider.interpreter(ScriptLanguage.JAVASCRIPT).toRealPath())
        .isEqualTo(node.toRealPath());
  }

  @Test
  void skipsABrokenShimForALaterInterpreterOfThePath() throws IOException {
    requireNode();
    final var broken = Files.createDirectory(bin.resolve("broken"));
    final var working = Files.createDirectory(bin.resolve("working"));
    Files.writeString(broken.resolve("node"), "#!/bin/sh\nexit 126\n");
    broken.resolve("node").toFile().setExecutable(true);
    Files.createSymbolicLink(working.resolve("node"), node);
    final var provider = provider(List.of(broken, working));

    provider.ensureExecutors();

    assertThat(provider.interpreter(ScriptLanguage.JAVASCRIPT)).isNotNull();
  }

  @Test
  void runsTheLanguagesOfTheInterpretersItHasAndFailsTheOthers() throws IOException {
    requireNode();
    Files.createSymbolicLink(bin.resolve("node"), node);
    shim("python3", "echo 'No version is set for command python3' >&2\nexit 126");
    final var provider = provider(List.of(bin));

    provider.ensureExecutors();

    assertThat(
            provider
                .execute(
                    ScriptLanguage.JAVASCRIPT,
                    request("export function execute() { return {}; }", Map.of(), 10_000))
                .outcome())
        .isEqualTo(ExecutionResponse.COMPLETED);
    final var python =
        provider.execute(
            ScriptLanguage.PYTHON,
            request("def execute(variables, context):\n    return {}\n", Map.of(), 10_000));
    assertThat(python.outcome()).isEqualTo(ExecutionResponse.FAILED);
    assertThat(python.error().code()).isEqualTo("PLATFORM_REJECTED");
    assertThat(python.error().message())
        .contains("Python 3.10 or later")
        .contains("No version is set for command python3");
  }

  @Test
  void startsAgainOnTheDirectoryOfScriptsThatRan() throws IOException {
    requireNode();
    requirePython();
    final var searchPath = List.of(link("node", node), link("python3", python));
    final var first = provider(searchPath);
    first.ensureExecutors();
    assertThat(
            first
                .execute(
                    ScriptLanguage.JAVASCRIPT,
                    request(
                        "import { writeFileSync } from 'node:fs';"
                            + " export function execute() { writeFileSync('left-behind', 'x'); return {}; }",
                        Map.of(),
                        10_000))
                .outcome())
        .isEqualTo(ExecutionResponse.COMPLETED);
    assertThat(
            first
                .execute(
                    ScriptLanguage.PYTHON,
                    request(
                        "def execute(variables, context):\n"
                            + "    open('left-behind', 'w').write('x')\n"
                            + "    return {}\n",
                        Map.of(),
                        10_000))
                .outcome())
        .isEqualTo(ExecutionResponse.COMPLETED);

    provider(searchPath).ensureExecutors();

    for (var language : ScriptLanguage.values()) {
      final var installed = directory.resolve(wrapperOf(language).executorName("", "local"));
      try (Stream<Path> files = Files.list(installed)) {
        assertThat(files.map(file -> file.getFileName().toString()).toList())
            .containsExactlyInAnyOrderElementsOf(wrapperOf(language).files().keySet());
      }
    }
  }

  @Test
  void findsAnInterpreterInstalledAfterTheStart() throws IOException {
    requireNode();
    requirePython();
    Files.createSymbolicLink(bin.resolve("node"), node);
    shim("python3", "echo 'No version is set for command python3' >&2\nexit 126");
    final var provider = provider(List.of(bin));
    provider.ensureExecutors();
    final var script =
        request("def execute(variables, context):\n    return {}\n", Map.of(), 10_000);
    assertThat(provider.execute(ScriptLanguage.PYTHON, script).error().code())
        .isEqualTo("PLATFORM_REJECTED");

    Files.delete(bin.resolve("python3"));
    Files.createSymbolicLink(bin.resolve("python3"), python);

    assertThat(provider.execute(ScriptLanguage.PYTHON, script).outcome())
        .isEqualTo(ExecutionResponse.COMPLETED);
  }

  @Test
  void refusesADirectoryThatOtherUsersCanWrite() throws IOException {
    requireNode();
    Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwxrwxr-x"));

    assertThatThrownBy(provider(List.of(link("node", node)))::ensureExecutors)
        .isInstanceOfSatisfying(
            ProvisioningException.class,
            e -> {
              assertThat(e.code()).isEqualTo(ErrorCode.PLATFORM_REJECTED);
              assertThat(e.getMessage()).contains("can be written by other users");
            });
  }

  @Test
  void refusesADirectoryThatIsALink() throws IOException {
    requireNode();
    final var linked = Files.createSymbolicLink(bin.resolve("linked"), directory);
    final var provider =
        new LocalManagedCodeProvider(
            linked, JSON, new InterpreterLocator(List.of(link("node", node))));

    assertThatThrownBy(provider::ensureExecutors)
        .isInstanceOf(ProvisioningException.class)
        .hasMessageContaining("not a link");
  }

  @Test
  void reportsAMissingCoreFileSoThatTheHandlerPreparesTheExecutorsAgain() throws IOException {
    requireNode();
    final var provider = provider(List.of(link("node", node)));
    provider.ensureExecutors();
    final var script = request("export function execute() { return {}; }", Map.of(), 10_000);
    final var installed =
        directory.resolve(wrapperOf(ScriptLanguage.JAVASCRIPT).executorName("", "local"));
    Files.delete(installed.resolve("executor_core.mjs"));

    final var response = provider.execute(ScriptLanguage.JAVASCRIPT, script);

    assertThat(response.isDeploymentMissing()).isTrue();
    assertThat(response.error().message()).contains("executor_core.mjs");
    deleteRecursively(installed);
    provider.ensureExecutors();
    assertThat(provider.execute(ScriptLanguage.JAVASCRIPT, script).outcome())
        .isEqualTo(ExecutionResponse.COMPLETED);
  }

  @Test
  void stopsAWrapperThatDoesNotReadItsInputWithinTheDeadline() throws IOException {
    shim("node", "case \"$1\" in -p) printf '%s\\n24.0.0\\n' \"$0\";; *) sleep 60;; esac");
    final var provider =
        new LocalManagedCodeProvider(
            directory, JSON, new InterpreterLocator(List.of(bin)), Duration.ofMillis(500));
    provider.ensureExecutors();
    final var input = Map.<String, Object>of("big", "x".repeat(8 * 1024 * 1024));

    final var start = System.nanoTime();
    final var response =
        provider.execute(
            ScriptLanguage.JAVASCRIPT,
            request("export function execute() { return {}; }", input, 500));

    assertThat(response.error().code()).isEqualTo("PLATFORM_EXECUTION_FAILED");
    assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(20));
  }

  @Test
  void stopsWhatTheWrapperStartedWhenTheWrapperIsKilled() throws Exception {
    final var pidFile = bin.resolve("script.pid");
    shim(
        "node",
        "case \"$1\" in -p) printf '%s\\n24.0.0\\n' \"$0\";;\n"
            + "*) sleep 60 & echo $! > '"
            + pidFile
            + "'; sleep 1; kill -9 $$;; esac");
    final var provider = provider(List.of(bin));
    provider.ensureExecutors();

    final var response =
        provider.execute(
            ScriptLanguage.JAVASCRIPT,
            request("export function execute() { return {}; }", Map.of(), 10_000));

    assertThat(response.error().code()).isEqualTo("PLATFORM_EXECUTION_FAILED");
    final var script = ProcessHandle.of(Long.parseLong(Files.readString(pidFile).strip()));
    final var deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
    while (script.isPresent() && script.get().isAlive() && System.nanoTime() < deadline) {
      Thread.sleep(50);
    }
    assertThat(script.isEmpty() || !script.get().isAlive())
        .as("the script the wrapper started is gone")
        .isTrue();
  }

  @Test
  void removesTheDirectoryOfACallWhoseWrapperWasKilled() throws IOException {
    shim(
        "node",
        "case \"$1\" in -p) printf '%s\\n24.0.0\\n' \"$0\";;\n"
            + "*) mkdir \"$EXECUTOR_TMP/run-1\"; echo secret > \"$EXECUTOR_TMP/run-1/user_script.mjs\";"
            + " kill -9 $$;; esac");
    final var provider = provider(List.of(bin));
    provider.ensureExecutors();

    final var response =
        provider.execute(
            ScriptLanguage.JAVASCRIPT,
            request("export function execute() { return {}; }", Map.of(), 10_000));

    assertThat(response.error().code()).isEqualTo("PLATFORM_EXECUTION_FAILED");
    try (Stream<Path> left = Files.list(directory.resolve("tmp"))) {
      assertThat(left.toList()).isEmpty();
    }
  }

  @Test
  void removesTheDirectoryOfACallThatTimedOut() throws IOException {
    shim(
        "node",
        "case \"$1\" in -p) printf '%s\\n24.0.0\\n' \"$0\";;\n"
            + "*) mkdir \"$EXECUTOR_TMP/run-1\"; echo secret > \"$EXECUTOR_TMP/run-1/user_script.mjs\";"
            + " sleep 60;; esac");
    final var provider =
        new LocalManagedCodeProvider(
            directory, JSON, new InterpreterLocator(List.of(bin)), Duration.ofMillis(300));
    provider.ensureExecutors();

    final var response =
        provider.execute(
            ScriptLanguage.JAVASCRIPT,
            request("export function execute() { return {}; }", Map.of(), 300));

    assertThat(response.error().code()).isEqualTo("PLATFORM_EXECUTION_FAILED");
    try (Stream<Path> left = Files.list(directory.resolve("tmp"))) {
      assertThat(left.toList()).isEmpty();
    }
  }

  @Test
  void keepsTheInterruptionOfAProbeInsteadOfRejectingThePlatform() throws IOException {
    shim("node", "sleep 30");
    final var provider = provider(List.of(bin));

    Thread.currentThread().interrupt();
    try {
      assertThatThrownBy(provider::ensureExecutors)
          .isInstanceOfSatisfying(
              ProvisioningException.class,
              e -> assertThat(e.code()).isEqualTo(ErrorCode.PLATFORM_INTERRUPTED));
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void logsWhatTheScriptWritesToItsConsole() throws IOException {
    requireNode();
    final var provider = provider(List.of(link("node", node)));
    provider.ensureExecutors();
    final var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    final var logger =
        (Logger) LoggerFactory.getLogger("io.camunda.connector.managedscript.local.script");
    logger.addAppender(appender);
    try {
      provider.execute(
          ScriptLanguage.JAVASCRIPT,
          request(
              "export function execute() { console.log('hello from the script'); return {}; }",
              Map.of(),
              10_000));
    } finally {
      logger.detachAppender(appender);
    }

    assertThat(appender.list)
        .singleElement()
        .satisfies(
            event ->
                assertThat(event.getFormattedMessage())
                    .contains("hello from the script")
                    .contains("execution 42"));
  }

  @Test
  void checksTheMinimumVersions() {
    assertThat(LocalRuntime.NODE.accepts("20.0.0")).isTrue();
    assertThat(LocalRuntime.NODE.accepts("24.20.0\n")).isTrue();
    assertThat(LocalRuntime.NODE.accepts("18.19.1")).isFalse();
    assertThat(LocalRuntime.PYTHON.accepts("3.10")).isTrue();
    assertThat(LocalRuntime.PYTHON.accepts("3.13")).isTrue();
    assertThat(LocalRuntime.PYTHON.accepts("3.9")).isFalse();
    assertThat(LocalRuntime.PYTHON.accepts("2.7")).isFalse();
    assertThat(LocalRuntime.PYTHON.accepts("not a version")).isFalse();
  }

  private LocalManagedCodeProvider provider(List<Path> searchPath) {
    return new LocalManagedCodeProvider(directory, JSON, new InterpreterLocator(searchPath));
  }

  private static ExecutionRequest request(
      String script, Map<String, Object> variables, long timeoutMs) {
    return new ExecutionRequest("42", script, variables, Map.of("jobKey", "42"), timeoutMs);
  }

  private static ExecutorWrapper wrapperOf(ScriptLanguage language) throws IOException {
    final var entry =
        language == ScriptLanguage.JAVASCRIPT ? "node/local_call.mjs" : "python/local_call.py";
    try (var in =
        LocalManagedCodeProvider.class.getResourceAsStream(
            "/io/camunda/connector/managedscript/local/" + entry)) {
      return ExecutorWrapper.of(language)
          .with(Map.of(Path.of(entry).getFileName().toString(), in.readAllBytes()));
    }
  }

  /** A {@code name} in the bin directory that is the interpreter at {@code target}. */
  private Path link(String name, Path target) throws IOException {
    final var directoryOfLink = Files.createTempDirectory(bin, "link");
    Files.createSymbolicLink(directoryOfLink.resolve(name), target);
    return directoryOfLink;
  }

  private void shim(String name, String body) throws IOException {
    final var shim = bin.resolve(name);
    Files.writeString(shim, "#!/bin/sh\n" + body + "\n", StandardCharsets.UTF_8);
    assertThat(shim.toFile().setExecutable(true)).isTrue();
  }

  private static void requireNode() {
    assumeTrue(node != null, "no Node.js 20 or later on the PATH");
  }

  private static void requirePython() {
    assumeTrue(python != null, "no Python 3.10 or later on the PATH");
  }

  private static Path realInterpreter(LocalRuntime runtime, Path scratch) {
    try {
      return InterpreterLocator.onPath().locate(runtime, scratch);
    } catch (ProvisioningException e) {
      return null;
    }
  }

  private static void deleteRecursively(Path path) throws IOException {
    try (var files = Files.walk(path)) {
      for (var file : files.sorted(java.util.Comparator.reverseOrder()).toList()) {
        Files.delete(file);
      }
    }
  }
}
