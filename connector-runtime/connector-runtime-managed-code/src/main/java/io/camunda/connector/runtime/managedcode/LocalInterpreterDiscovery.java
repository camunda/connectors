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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

final class LocalInterpreterDiscovery {

  private static final Duration VERSION_TIMEOUT = Duration.ofSeconds(2);
  private static final Pattern VERSION_PATTERN =
      Pattern.compile("(?<!\\d)(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

  private LocalInterpreterDiscovery() {}

  static DiscoveryReport discover() {
    final var statuses = new EnumMap<LocalScriptRuntime, RuntimeStatus>(LocalScriptRuntime.class);
    for (var runtime : LocalScriptRuntime.values()) {
      statuses.put(runtime, discover(runtime));
    }
    return new DiscoveryReport(statuses);
  }

  private static RuntimeStatus discover(LocalScriptRuntime runtime) {
    final var executable = findOnPath(runtime.executableNames());
    if (executable.isEmpty()) {
      return RuntimeStatus.unavailable(
          runtime, "No %s interpreter was found on PATH".formatted(runtime.language()));
    }
    try {
      final var output = readVersion(executable.orElseThrow());
      final var matcher = VERSION_PATTERN.matcher(output);
      if (!matcher.find()) {
        return RuntimeStatus.unavailable(
            runtime,
            "Could not parse the version reported by %s: %s"
                .formatted(executable.orElseThrow(), bounded(output)));
      }
      final int major = Integer.parseInt(matcher.group(1));
      final int minor = Integer.parseInt(matcher.group(2));
      if (major < runtime.minimumMajor()
          || (major == runtime.minimumMajor() && minor < runtime.minimumMinor())) {
        return RuntimeStatus.unavailable(
            runtime,
            "%s %d.%d or newer is required, but %s reported %s"
                .formatted(
                    runtime.language(),
                    runtime.minimumMajor(),
                    runtime.minimumMinor(),
                    executable.orElseThrow(),
                    bounded(output)));
      }
      return RuntimeStatus.available(runtime, executable.orElseThrow(), bounded(output));
    } catch (IOException e) {
      final var message = e.getMessage();
      return RuntimeStatus.unavailable(
          runtime, message == null || message.isBlank() ? e.getClass().getSimpleName() : message);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return RuntimeStatus.unavailable(runtime, "Interpreter discovery was interrupted");
    }
  }

  private static Optional<Path> findOnPath(List<String> names) {
    final var path = System.getenv("PATH");
    if (path == null || path.isBlank()) {
      return Optional.empty();
    }
    final List<String> platformNames = new ArrayList<>();
    for (var name : names) {
      platformNames.add(name);
      if (isWindows()) {
        platformNames.add(name + ".exe");
      }
    }
    for (var directory : path.split(Pattern.quote(java.io.File.pathSeparator))) {
      if (directory.isBlank()) {
        continue;
      }
      for (var name : platformNames) {
        final var candidate = Path.of(directory, name).toAbsolutePath().normalize();
        if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
          return Optional.of(candidate);
        }
      }
    }
    return Optional.empty();
  }

  private static String readVersion(Path executable) throws IOException, InterruptedException {
    final var process =
        new ProcessBuilder(executable.toString(), "--version").redirectErrorStream(true).start();
    if (!process.waitFor(VERSION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
      process.destroyForcibly();
      process.waitFor();
      throw new IOException("Version command timed out for " + executable);
    }
    final var output = process.getInputStream().readNBytes(4_096);
    if (process.exitValue() != 0) {
      throw new IOException(
          "Version command for %s exited with code %d".formatted(executable, process.exitValue()));
    }
    return new String(output, StandardCharsets.UTF_8).trim();
  }

  private static String bounded(String value) {
    final var normalized = value == null ? "" : value.strip();
    return normalized.length() <= 256 ? normalized : normalized.substring(0, 256);
  }

  private static boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase().contains("win");
  }

  record RuntimeStatus(
      LocalScriptRuntime runtime,
      Optional<Path> executable,
      String version,
      boolean available,
      String diagnostic) {

    static RuntimeStatus available(LocalScriptRuntime runtime, Path executable, String version) {
      return new RuntimeStatus(runtime, Optional.of(executable), version, true, "");
    }

    static RuntimeStatus unavailable(LocalScriptRuntime runtime, String diagnostic) {
      return new RuntimeStatus(runtime, Optional.empty(), "", false, diagnostic);
    }
  }

  record DiscoveryReport(Map<LocalScriptRuntime, RuntimeStatus> statuses) {

    DiscoveryReport {
      statuses = Map.copyOf(statuses);
    }

    RuntimeStatus require(LocalScriptRuntime runtime) {
      final var status = statuses.get(runtime);
      if (status == null || !status.available()) {
        throw new IllegalStateException(
            status == null
                ? "No discovery result is available for " + runtime.language()
                : status.diagnostic());
      }
      return status;
    }
  }
}
