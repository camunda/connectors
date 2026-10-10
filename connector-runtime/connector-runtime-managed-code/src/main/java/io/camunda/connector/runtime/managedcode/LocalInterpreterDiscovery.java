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
import java.util.LinkedHashSet;
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
    final var statuses = new EnumMap<ScriptLanguage, RuntimeStatus>(ScriptLanguage.class);
    for (var runtime : ScriptLanguage.values()) {
      statuses.put(runtime, discover(runtime, System.getenv("PATH")));
    }
    return new DiscoveryReport(statuses);
  }

  static RuntimeStatus discover(ScriptLanguage runtime, String path) {
    final var executables = findOnPath(runtime.executableNames(), path);
    if (executables.isEmpty()) {
      return RuntimeStatus.unavailable(
          runtime, "No %s interpreter was found on PATH".formatted(runtime.language()));
    }
    final List<String> diagnostics = new ArrayList<>();
    for (var executable : executables) {
      try {
        final var output = readVersion(executable);
        final var matcher = VERSION_PATTERN.matcher(output);
        if (!matcher.find()) {
          diagnostics.add(
              "could not parse the version reported by %s: %s"
                  .formatted(executable, bounded(output)));
          continue;
        }
        final int major = Integer.parseInt(matcher.group(1));
        final int minor = Integer.parseInt(matcher.group(2));
        if (major < runtime.minimumMajor()
            || (major == runtime.minimumMajor() && minor < runtime.minimumMinor())) {
          diagnostics.add(
              "%s reported %s, but %s %d.%d or newer is required"
                  .formatted(
                      executable,
                      bounded(output),
                      runtime.language(),
                      runtime.minimumMajor(),
                      runtime.minimumMinor()));
          continue;
        }
        return RuntimeStatus.available(runtime, executable, bounded(output));
      } catch (IOException e) {
        final var message = e.getMessage();
        diagnostics.add(
            "%s: %s"
                .formatted(
                    executable,
                    message == null || message.isBlank() ? e.getClass().getSimpleName() : message));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return RuntimeStatus.unavailable(runtime, "Interpreter discovery was interrupted");
      }
    }
    return RuntimeStatus.unavailable(
        runtime,
        "No usable %s interpreter was found on PATH: %s"
            .formatted(runtime.language(), String.join("; ", diagnostics)));
  }

  private static List<Path> findOnPath(List<String> names, String path) {
    if (path == null || path.isBlank()) {
      return List.of();
    }
    final List<String> platformNames = new ArrayList<>();
    for (var name : names) {
      platformNames.add(name);
      if (isWindows()) {
        platformNames.add(name + ".exe");
      }
    }
    final var candidates = new LinkedHashSet<Path>();
    for (var directory : path.split(Pattern.quote(java.io.File.pathSeparator))) {
      if (directory.isBlank()) {
        continue;
      }
      for (var name : platformNames) {
        final var candidate = Path.of(directory, name).toAbsolutePath().normalize();
        if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
          candidates.add(candidate);
        }
      }
    }
    return List.copyOf(candidates);
  }

  private static String readVersion(Path executable) throws IOException, InterruptedException {
    final var processBuilder =
        new ProcessBuilder(executable.toString(), "--version").redirectErrorStream(true);
    LocalProcessEnvironment.configure(
        processBuilder.environment(), Path.of(System.getProperty("java.io.tmpdir")));
    final var process = processBuilder.start();
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
      ScriptLanguage runtime,
      Optional<Path> executable,
      String version,
      boolean available,
      String diagnostic) {

    static RuntimeStatus available(ScriptLanguage runtime, Path executable, String version) {
      return new RuntimeStatus(runtime, Optional.of(executable), version, true, "");
    }

    static RuntimeStatus unavailable(ScriptLanguage runtime, String diagnostic) {
      return new RuntimeStatus(runtime, Optional.empty(), "", false, diagnostic);
    }
  }

  record DiscoveryReport(Map<ScriptLanguage, RuntimeStatus> statuses) {

    DiscoveryReport {
      statuses = Map.copyOf(statuses);
    }

    RuntimeStatus require(ScriptLanguage runtime) {
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
