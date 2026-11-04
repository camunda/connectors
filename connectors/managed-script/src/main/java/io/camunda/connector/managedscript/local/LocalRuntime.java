/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.managedscript.local;

import io.camunda.connector.managedscript.api.ScriptLanguage;
import java.util.List;

/**
 * What the local provider needs to know about the interpreter of a language: how to find it, how to
 * ask it for its real path and version, and how to start the wrapper with it.
 */
enum LocalRuntime {
  NODE(
      ScriptLanguage.JAVASCRIPT,
      "Node.js",
      "node",
      List.of("-p", "process.execPath + '\\n' + process.versions.node"),
      List.of(),
      "local_call.mjs",
      "node",
      20,
      0),
  PYTHON(
      ScriptLanguage.PYTHON,
      "Python",
      "python3",
      List.of(
          "-I", "-c", "import sys; print(sys.executable); print('%d.%d' % sys.version_info[:2])"),
      List.of("-I"),
      "local_call.py",
      "python",
      3,
      10);

  private final ScriptLanguage language;
  private final String displayName;
  private final String executable;

  /** Arguments that make the interpreter print its real path, then its version. */
  private final List<String> probeArguments;

  /** Arguments that precede the path of the entry point of the wrapper. */
  private final List<String> runArguments;

  /** File of the wrapper, added by this provider, that runs one call read from stdin. */
  private final String entryPoint;

  /** Directory, next to this class, of the resource of the entry point. */
  private final String resourceDirectory;

  private final int minimumMajor;
  private final int minimumMinor;

  LocalRuntime(
      ScriptLanguage language,
      String displayName,
      String executable,
      List<String> probeArguments,
      List<String> runArguments,
      String entryPoint,
      String resourceDirectory,
      int minimumMajor,
      int minimumMinor) {
    this.language = language;
    this.displayName = displayName;
    this.executable = executable;
    this.probeArguments = probeArguments;
    this.runArguments = runArguments;
    this.entryPoint = entryPoint;
    this.resourceDirectory = resourceDirectory;
    this.minimumMajor = minimumMajor;
    this.minimumMinor = minimumMinor;
  }

  static LocalRuntime of(ScriptLanguage language) {
    for (var runtime : values()) {
      if (runtime.language == language) {
        return runtime;
      }
    }
    throw new IllegalArgumentException("No local runtime for " + language);
  }

  ScriptLanguage language() {
    return language;
  }

  String displayName() {
    return displayName;
  }

  /** Name of the interpreter on the PATH. */
  String executable() {
    return executable;
  }

  List<String> probeArguments() {
    return probeArguments;
  }

  List<String> runArguments() {
    return runArguments;
  }

  String entryPoint() {
    return entryPoint;
  }

  String resourcePath() {
    return "/io/camunda/connector/managedscript/local/" + resourceDirectory + "/" + entryPoint;
  }

  /** Such as {@code Node.js 20}. */
  String minimumVersion() {
    return displayName + " " + minimumMajor + (minimumMinor == 0 ? "" : "." + minimumMinor);
  }

  /** Whether a version such as {@code 24.20.0} or {@code 3.11} is at least the minimum. */
  boolean accepts(String version) {
    final var parts = version.strip().split("\\.");
    try {
      final int major = Integer.parseInt(parts[0]);
      final int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
      return major > minimumMajor || (major == minimumMajor && minor >= minimumMinor);
    } catch (NumberFormatException e) {
      return false;
    }
  }
}
