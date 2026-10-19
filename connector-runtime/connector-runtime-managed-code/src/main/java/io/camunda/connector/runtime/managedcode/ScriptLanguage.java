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

import java.util.List;
import java.util.Locale;

/** Supported managed-script languages, with their local interpreter names. */
public enum ScriptLanguage {
  PYTHON("python", ".py", "python", "python3", List.of("python3", "python"), 3, 10),
  JAVASCRIPT("javascript", ".js", "node", "nodejs", List.of("node"), 20, 0);

  private final String language;
  private final String extension;
  private final String runtimePrefix;
  private final String defaultRuntime;
  private final List<String> executableNames;
  private final int minimumMajor;
  private final int minimumMinor;

  ScriptLanguage(
      String language,
      String extension,
      String runtimePrefix,
      String defaultRuntime,
      List<String> executableNames,
      int minimumMajor,
      int minimumMinor) {
    this.language = language;
    this.extension = extension;
    this.runtimePrefix = runtimePrefix;
    this.defaultRuntime = defaultRuntime;
    this.executableNames = executableNames;
    this.minimumMajor = minimumMajor;
    this.minimumMinor = minimumMinor;
  }

  public String language() {
    return language;
  }

  public String extension() {
    return extension;
  }

  /** The runtime used when the task does not name one. */
  public String defaultRuntime() {
    return defaultRuntime;
  }

  List<String> executableNames() {
    return executableNames;
  }

  int minimumMajor() {
    return minimumMajor;
  }

  int minimumMinor() {
    return minimumMinor;
  }

  /**
   * Resolves the language and checks that the runtime and the script file name are compatible.
   * Without a language, it follows the extension of the script file name.
   *
   * @param language may be {@code null}
   * @param runtime may be {@code null} for the default runtime of the language
   * @throws IllegalArgumentException when the combination is unsupported
   */
  static ScriptLanguage from(String language, String runtime, String resourceName) {
    final var candidate =
        language == null ? fromExtension(resourceName) : fromLanguageName(language);
    if (!resourceName.endsWith(candidate.extension)) {
      throw new IllegalArgumentException(
          "%s scripts must use the %s extension, but the script resource is '%s'"
              .formatted(candidate.language, candidate.extension, resourceName));
    }
    if (runtime != null && !runtime.toLowerCase(Locale.ROOT).startsWith(candidate.runtimePrefix)) {
      throw new IllegalArgumentException(
          "Runtime '%s' is incompatible with language '%s'".formatted(runtime, candidate.language));
    }
    return candidate;
  }

  private static ScriptLanguage fromLanguageName(String language) {
    for (var candidate : values()) {
      if (candidate.language.equalsIgnoreCase(language)) {
        return candidate;
      }
    }
    throw new IllegalArgumentException(
        "Unsupported managed-script language '%s'".formatted(language));
  }

  private static ScriptLanguage fromExtension(String resourceName) {
    for (var candidate : values()) {
      if (resourceName.endsWith(candidate.extension)) {
        return candidate;
      }
    }
    throw new IllegalArgumentException(
        "Cannot derive the managed-script language from the script resource '%s': use a .py or .js file"
            .formatted(resourceName));
  }
}
