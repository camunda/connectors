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
import java.util.HashSet;
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

  /** The process did not start, so nothing of the call ran. */
  static final class NotStartedException extends IOException {
    NotStartedException(IOException cause) {
      super(cause.getMessage(), cause);
    }
  }

  /** How often the descendants of the process are noted while it runs. */
  private static final long POLL_MILLIS = 200;

  private ProcessRunner() {}

  /**
   * @param stdin written to the process while it is waited for, so that a process that does not
   *     read it cannot keep the deadline from applying; may be {@code null}
   * @param maxErrorBytes how much of the standard error is kept
   * @param scratch directory, private to this runtime, of the temporary files
   * @throws NotStartedException when the process cannot be started; any other {@link IOException}
   *     may come after the process ran
   */
  static Result run(
      ProcessBuilder builder,
      byte[] stdin,
      Duration timeout,
      int maxOutputBytes,
      int maxErrorBytes,
      Path scratch)
      throws IOException, InterruptedException {
    final Path stdout;
    final Path stderr;
    try {
      stdout = Files.createTempFile(scratch, "stdout", ".tmp");
      stderr = Files.createTempFile(scratch, "stderr", ".tmp");
    } catch (IOException e) {
      throw new NotStartedException(e);
    }
    Process process = null;
    // Processes the process started, noted while it runs: once it is gone, for a crash or a kill,
    // they are no longer its descendants, and would run on without anything enforcing a deadline.
    final var started = new HashSet<ProcessHandle>();
    try {
      try {
        process = builder.redirectOutput(stdout.toFile()).redirectError(stderr.toFile()).start();
      } catch (IOException e) {
        throw new NotStartedException(e);
      }
      final var in = process.getOutputStream();
      if (stdin == null) {
        in.close();
      } else {
        Thread.ofVirtual()
            .start(() -> {
              try (in) {
                in.write(stdin);
              } catch (IOException e) {
                // The process ended before it read its input: its exit code tells why.
              }
            });
      }
      final long deadline = System.nanoTime() + timeout.toNanos();
      boolean exited;
      do {
        final long remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
        exited =
            process.waitFor(Math.max(1, Math.min(POLL_MILLIS, remaining)), TimeUnit.MILLISECONDS);
        if (!exited) {
          process.descendants().forEach(started::add);
        }
      } while (!exited && System.nanoTime() < deadline);
      if (!exited) {
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
      started.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
      deleteQuietly(stdout);
      deleteQuietly(stderr);
    }
  }

  /** A file left behind must not replace the result of a call that ran. */
  private static void deleteQuietly(Path file) {
    try {
      Files.deleteIfExists(file);
    } catch (IOException e) {
      // In the scratch directory, which only the user of the runtime can read.
    }
  }

  private static String head(Path file, int maxBytes) throws IOException {
    try (var in = Files.newInputStream(file)) {
      return new String(in.readNBytes(maxBytes), StandardCharsets.UTF_8).strip();
    }
  }
}
