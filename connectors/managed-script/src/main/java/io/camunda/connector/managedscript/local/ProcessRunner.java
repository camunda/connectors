/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.managedscript.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Runs a process to its end, or to a deadline, with its standard output and error in files so that
 * the deadline applies before anything is read: reading a pipe to its end waits for a hung process
 * as long as the process does.
 */
final class ProcessRunner {

  /**
   * @param stdout empty when {@code timedOut} or {@code outputTooLarge}
   * @param stderr the beginning of the standard error
   */
  record Result(
      boolean timedOut, boolean outputTooLarge, int exitCode, byte[] stdout, String stderr) {}

  private ProcessRunner() {}

  /**
   * @param stdin written to the process while it is waited for, so that a process that does not
   *     read it cannot keep the deadline from applying; may be {@code null}
   * @param maxErrorBytes how much of the standard error is kept
   * @param scratch directory, private to this runtime, of the temporary files
   */
  static Result run(
      ProcessBuilder builder,
      byte[] stdin,
      Duration timeout,
      int maxOutputBytes,
      int maxErrorBytes,
      Path scratch)
      throws IOException, InterruptedException {
    final var stdout = Files.createTempFile(scratch, "stdout", ".tmp");
    final var stderr = Files.createTempFile(scratch, "stderr", ".tmp");
    Process process = null;
    try {
      process = builder.redirectOutput(stdout.toFile()).redirectError(stderr.toFile()).start();
      final var in = process.getOutputStream();
      if (stdin == null) {
        in.close();
      } else {
        Thread.ofVirtual()
            .start(
                () -> {
                  try (in) {
                    in.write(stdin);
                  } catch (IOException e) {
                    // The process ended before it read its input: its exit code tells why.
                  }
                });
      }
      if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
        return new Result(true, false, -1, new byte[0], "");
      }
      final var error = head(stderr, maxErrorBytes);
      if (Files.size(stdout) > maxOutputBytes) {
        return new Result(false, true, process.exitValue(), new byte[0], error);
      }
      return new Result(false, false, process.exitValue(), Files.readAllBytes(stdout), error);
    } finally {
      if (process != null && process.isAlive()) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
      }
      Files.deleteIfExists(stdout);
      Files.deleteIfExists(stderr);
    }
  }

  private static String head(Path file, int maxBytes) throws IOException {
    try (var in = Files.newInputStream(file)) {
      return new String(in.readNBytes(maxBytes), StandardCharsets.UTF_8).strip();
    }
  }
}
