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

    final var builder =
        new ProcessBuilder(command).directory(task.toFile()).redirectErrorStream(true);
    builder.environment().put("EXECUTOR_TMP", tmp.toString());
    final var process = builder.start();
    final var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();

    assertThat(process.exitValue()).as(output).isZero();
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
              .start();
      process.getInputStream().readAllBytes();
      return process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0;
    } catch (IOException | InterruptedException e) {
      return false;
    }
  }
}
