/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.managedscript.gcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.camunda.connector.managedscript.api.ScriptLanguage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Loads the Cloud Run entry points on the interpreters of the host, as the Functions Framework does
 * when an instance or its server starts, with a stub of the Functions Framework that prints the
 * functions it is given: a test needing an interpreter that is not installed is skipped.
 */
@DisabledOnOs(OS.WINDOWS)
class CloudRunEntryPointTest {

  /** The variables the provider sets on every function. */
  private static final Map<String, String> SETTINGS =
      Map.of("EXECUTOR_FUNCTION_TIMEOUT_MS", "35000", "EXECUTOR_MEMORY_MB", "512");

  @TempDir Path source;
  @TempDir Path stubs;
  @TempDir Path tmp;
  @TempDir Path logs;

  @ParameterizedTest
  @EnumSource(ScriptLanguage.class)
  void removesWhatAPreviousServerLeftInTmpThenServesTheHandler(ScriptLanguage language)
      throws Exception {
    final var command = load(language);
    Files.writeString(tmp.resolve("user_script.py"), "secret of a previous call");
    Files.createDirectories(tmp.resolve("run-1/nested"));
    Files.writeString(tmp.resolve("run-1/nested/output"), "secret of a previous call");
    // A script can take the permissions off a directory, so that its content cannot be removed.
    Files.setPosixFilePermissions(
        tmp.resolve("run-1/nested"), PosixFilePermissions.fromString("r-x------"));
    Files.setPosixFilePermissions(
        tmp.resolve("run-1"), PosixFilePermissions.fromString("r-x------"));

    final var result = run(command, SETTINGS);

    assertThat(result.exitValue()).as(result.output()).isZero();
    assertThat(result.output()).contains("registered handler");
    try (Stream<Path> left = Files.list(tmp)) {
      assertThat(left.toList()).isEmpty();
    }
  }

  @ParameterizedTest
  @EnumSource(ScriptLanguage.class)
  void keepsWhatItCannotRemoveFromTmpAndStarts(ScriptLanguage language) throws Exception {
    final var command = load(language);
    // As the "run" entry a new Cloud Run container has in /tmp, which the server cannot remove.
    Files.writeString(tmp.resolve("run"), "of the platform");
    Files.setPosixFilePermissions(tmp, PosixFilePermissions.fromString("r-x------"));
    try {
      final var result = run(command, SETTINGS);

      assertThat(result.exitValue()).as(result.output()).isZero();
      assertThat(result.output())
          .contains("registered handler")
          .contains("Kept in " + tmp + ", which the server cannot remove: run");
    } finally {
      Files.setPosixFilePermissions(tmp, PosixFilePermissions.fromString("rwx------"));
    }
    assertThat(tmp.resolve("run")).exists();
  }

  @ParameterizedTest
  @EnumSource(ScriptLanguage.class)
  void refusesToStartWithoutTheFunctionTimeout(ScriptLanguage language) throws Exception {
    final var command = load(language);

    final var result = run(command, Map.of("EXECUTOR_MEMORY_MB", "512"));

    assertThat(result.exitValue()).as(result.output()).isNotZero();
    assertThat(result.output())
        .contains("EXECUTOR_FUNCTION_TIMEOUT_MS")
        .doesNotContain("registered handler");
  }

  @ParameterizedTest
  @EnumSource(ScriptLanguage.class)
  void refusesToStartWithoutTheMemory(ScriptLanguage language) throws Exception {
    final var command = load(language);

    final var result = run(command, Map.of("EXECUTOR_FUNCTION_TIMEOUT_MS", "35000"));

    assertThat(result.exitValue()).as(result.output()).isNotZero();
    assertThat(result.output()).contains("EXECUTOR_MEMORY_MB").doesNotContain("registered handler");
  }

  /** Writes the package and the stub of the Functions Framework; returns the loading command. */
  private List<String> load(ScriptLanguage language) throws IOException {
    final var command =
        language == ScriptLanguage.PYTHON
            ? List.of("python3", "-c", "import main")
            : List.of("node", "--input-type=module", "-e", "await import('./index.mjs')");
    assumeTrue(runs(command.getFirst()), "no " + command.getFirst() + " on the PATH");
    for (var file : CloudRunExecutor.of(language).wrapper().files().entrySet()) {
      Files.write(source.resolve(file.getKey()), file.getValue());
    }
    if (language == ScriptLanguage.PYTHON) {
      Files.writeString(
          stubs.resolve("functions_framework.py"),
          """
          def http(function):
              print("registered " + function.__name__)
              return function
          """);
    } else {
      final var framework =
          Files.createDirectories(source.resolve("node_modules/@google-cloud/functions-framework"));
      Files.writeString(
          framework.resolve("package.json"),
          "{\"name\": \"@google-cloud/functions-framework\", \"main\": \"index.js\"}");
      Files.writeString(
          framework.resolve("index.js"),
          "exports.http = (name) => console.log(`registered ${name}`);");
    }
    return command;
  }

  private Result run(List<String> command, Map<String, String> environment) throws Exception {
    final var builder =
        new ProcessBuilder(command).directory(source.toFile()).redirectErrorStream(true);
    builder.environment().remove("EXECUTOR_FUNCTION_TIMEOUT_MS");
    builder.environment().remove("EXECUTOR_MEMORY_MB");
    builder.environment().put("EXECUTOR_TMP", tmp.toString());
    builder.environment().put("PYTHONPATH", stubs.toString());
    builder.environment().putAll(environment);
    // To a file, so that the deadline applies to an adapter that hangs: reading a pipe to its end
    // waits as long as the process does.
    final var log = Files.createTempFile(logs, "adapter", ".log");
    final var process = builder.redirectOutput(log.toFile()).start();
    try {
      assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
      return new Result(process.exitValue(), Files.readString(log, StandardCharsets.UTF_8));
    } finally {
      process.destroyForcibly();
    }
  }

  private boolean runs(String interpreter) {
    try {
      final var process =
          new ProcessBuilder(interpreter, "--version")
              .directory(source.toFile())
              .redirectErrorStream(true)
              .redirectOutput(ProcessBuilder.Redirect.DISCARD)
              .start();
      try {
        return process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0;
      } finally {
        process.destroyForcibly();
      }
    } catch (IOException | InterruptedException e) {
      return false;
    }
  }

  private record Result(int exitValue, String output) {}
}
