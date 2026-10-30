/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.managedscript.local;

import io.camunda.connector.managedscript.api.ErrorCode;
import io.camunda.connector.managedscript.api.ProvisioningException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Finds the interpreter of a runtime on a search path, and the real file behind it.
 *
 * <p>A version manager such as asdf puts a shim on the PATH that picks a version from the current
 * directory. The wrapper runs from other directories, where the shim fails, so the interpreter
 * itself is asked for its real path, once, from the directory of the runtime.
 */
final class InterpreterLocator {

  private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(10);
  private static final int MAX_PROBE_BYTES = 4096;
  private static final int MAX_REJECTIONS_SHOWN = 3;
  private static final int MAX_REASON_LENGTH = 200;

  private final List<Path> searchPath;

  InterpreterLocator(List<Path> searchPath) {
    this.searchPath = List.copyOf(searchPath);
  }

  /** The directories of the {@code PATH} of this process. */
  static InterpreterLocator onPath() {
    final var path = System.getenv().getOrDefault("PATH", "");
    final var directories = new ArrayList<Path>();
    Arrays.stream(path.split(File.pathSeparator))
        .filter(directory -> !directory.isBlank())
        .forEach(
            directory -> {
              try {
                directories.add(Path.of(directory).toAbsolutePath());
              } catch (InvalidPathException e) {
                // Not a directory of this host.
              }
            });
    return new InterpreterLocator(directories);
  }

  /**
   * @param scratch directory for the temporary files of the probes
   * @return the real path of the first interpreter of the search path that is recent enough
   * @throws ProvisioningException when there is none
   */
  Path locate(LocalRuntime runtime, Path scratch) {
    final var rejections = new ArrayList<String>();
    for (var directory : searchPath) {
      final var candidate = directory.resolve(runtime.executable());
      if (!Files.isRegularFile(candidate) || !Files.isExecutable(candidate)) {
        continue;
      }
      final var rejection = new String[1];
      final var real = probe(runtime, candidate, scratch, rejection);
      if (real != null) {
        return real;
      }
      rejections.add(candidate + ": " + rejection[0]);
    }
    final var message =
        new StringBuilder(runtime.minimumVersion()).append(" or later was not found on the PATH");
    if (!rejections.isEmpty()) {
      message
          .append(": ")
          .append(
              String.join(
                  "; ", rejections.subList(0, Math.min(rejections.size(), MAX_REJECTIONS_SHOWN))));
    }
    throw new ProvisioningException(ErrorCode.PLATFORM_REJECTED, message.toString());
  }

  private static Path probe(LocalRuntime runtime, Path candidate, Path scratch, String[] reason) {
    final var command = new ArrayList<String>();
    command.add(candidate.toString());
    command.addAll(runtime.probeArguments());
    try {
      // The environment of the runtime, which a version manager needs to pick a version.
      final var result =
          ProcessRunner.run(
              new ProcessBuilder(command),
              null,
              PROBE_TIMEOUT,
              MAX_PROBE_BYTES,
              MAX_PROBE_BYTES,
              scratch);
      if (result.timedOut()) {
        reason[0] = "no answer within " + PROBE_TIMEOUT.toSeconds() + " seconds";
        return null;
      }
      if (result.exitCode() != 0) {
        reason[0] = firstLine(result.stderr(), "exited with code " + result.exitCode());
        return null;
      }
      final var lines = new String(result.stdout(), StandardCharsets.UTF_8).strip().split("\\R");
      if (lines.length != 2 || lines[0].isBlank()) {
        reason[0] = "unexpected answer to the version probe";
        return null;
      }
      if (!runtime.accepts(lines[1])) {
        reason[0] = "version " + lines[1].strip() + " is older than " + runtime.minimumVersion();
        return null;
      }
      final var real = Path.of(lines[0].strip());
      if (!Files.isExecutable(real)) {
        reason[0] = "its real path " + real + " is not an executable";
        return null;
      }
      return real;
    } catch (IOException | InvalidPathException e) {
      reason[0] = "cannot be started: " + e.getMessage();
      return null;
    } catch (InterruptedException e) {
      // Not a missing interpreter: the job keeps its retries.
      Thread.currentThread().interrupt();
      throw new ProvisioningException(
          ErrorCode.PLATFORM_INTERRUPTED,
          "Interrupted while looking for " + runtime.minimumVersion());
    }
  }

  private static String firstLine(String text, String fallback) {
    final var line = text.lines().filter(l -> !l.isBlank()).findFirst().orElse(fallback).strip();
    return line.length() > MAX_REASON_LENGTH ? line.substring(0, MAX_REASON_LENGTH) + "…" : line;
  }
}
