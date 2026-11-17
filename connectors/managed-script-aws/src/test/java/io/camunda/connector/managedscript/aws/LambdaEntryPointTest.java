/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.managedscript.aws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.camunda.connector.managedscript.api.ScriptLanguage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Loads the Lambda entry points on the interpreters of the host, as Lambda does when it starts or
 * restarts the runtime: a test needing one that is not installed is skipped.
 */
@DisabledOnOs(OS.WINDOWS)
class LambdaEntryPointTest {

  @TempDir Path task;
  @TempDir Path tmp;
  @TempDir Path logs;

  @ParameterizedTest
  @EnumSource(ScriptLanguage.class)
  void removesWhatAPreviousRuntimeLeftInTmpBeforeTheExecutorStarts(ScriptLanguage language)
      throws Exception {
    final var command =
        language == ScriptLanguage.PYTHON
            ? List.of("python3", "-c", "import lambda_handler")
            : List.of("node", "--input-type=module", "-e", "await import('./index.mjs')");
    assumeTrue(runs(command.getFirst()), "no " + command.getFirst() + " on the PATH");
    for (var file : LambdaExecutor.of(language).wrapper().files().entrySet()) {
      Files.write(task.resolve(file.getKey()), file.getValue());
    }
    Files.writeString(tmp.resolve("user_script.py"), "secret of a previous call");
    Files.createDirectories(tmp.resolve("run-1/nested"));
    Files.writeString(tmp.resolve("run-1/nested/output"), "secret of a previous call");
    // A script can take the permissions off a directory, so that its content cannot be removed.
    Files.setPosixFilePermissions(
        tmp.resolve("run-1/nested"), PosixFilePermissions.fromString("r-x------"));
    Files.setPosixFilePermissions(
        tmp.resolve("run-1"), PosixFilePermissions.fromString("r-x------"));

    final var builder =
        new ProcessBuilder(command).directory(task.toFile()).redirectErrorStream(true);
    builder.environment().put("EXECUTOR_TMP", tmp.toString());
    // Set by Lambda on every function.
    builder.environment().put("AWS_LAMBDA_FUNCTION_MEMORY_SIZE", "512");
    // To a file, so that the deadline applies to an entry point that hangs: reading a pipe to its
    // end waits as long as the process does.
    final var log = Files.createTempFile(logs, "entry-point", ".log");
    final var process = builder.redirectOutput(log.toFile()).start();
    try {
      assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
    } finally {
      process.destroyForcibly();
    }

    assertThat(process.exitValue()).as(Files.readString(log, StandardCharsets.UTF_8)).isZero();
    try (Stream<Path> left = Files.list(tmp)) {
      assertThat(left.toList()).isEmpty();
    }
  }

  private boolean runs(String interpreter) {
    try {
      final var process =
          new ProcessBuilder(interpreter, "--version")
              .directory(task.toFile())
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
}
