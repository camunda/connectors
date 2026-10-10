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
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;

/** Supported managed-script languages, with the generated wrapper and local interpreter names. */
public enum ScriptLanguage {
  PYTHON("python", ".py", "python", "python_wrapper.py", List.of("python3", "python"), 3, 10),
  JAVASCRIPT("javascript", ".js", "node", "node_wrapper.mjs", List.of("node"), 20, 0);

  private final String language;
  private final String extension;
  private final String runtimePrefix;
  private final String wrapperResource;
  private final List<String> executableNames;
  private final int minimumMajor;
  private final int minimumMinor;
  private volatile byte[] wrapper;

  ScriptLanguage(
      String language,
      String extension,
      String runtimePrefix,
      String wrapperResource,
      List<String> executableNames,
      int minimumMajor,
      int minimumMinor) {
    this.language = language;
    this.extension = extension;
    this.runtimePrefix = runtimePrefix;
    this.wrapperResource = wrapperResource;
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

  String wrapperResource() {
    return wrapperResource;
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

  /** The generated wrapper that invokes the user entrypoint; part of the artifact digest. */
  public byte[] wrapper() {
    var bytes = wrapper;
    if (bytes == null) {
      final var resourceName = "/managed-code-local/" + wrapperResource;
      try (var stream = ScriptLanguage.class.getResourceAsStream(resourceName)) {
        if (stream == null) {
          throw new IllegalStateException("Missing managed-script wrapper: " + resourceName);
        }
        bytes = stream.readAllBytes();
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      wrapper = bytes;
    }
    return bytes;
  }

  /**
   * Resolves the language and checks that the runtime and the script file name are compatible.
   *
   * @throws IllegalArgumentException when the combination is unsupported
   */
  static ScriptLanguage from(String language, String runtime, String resourceName) {
    for (var candidate : values()) {
      if (candidate.language.equalsIgnoreCase(language)) {
        if (!resourceName.endsWith(candidate.extension)) {
          throw new IllegalArgumentException(
              "%s scripts must use the %s extension, but the script resource is '%s'"
                  .formatted(candidate.language, candidate.extension, resourceName));
        }
        if (!runtime.toLowerCase(Locale.ROOT).startsWith(candidate.runtimePrefix)) {
          throw new IllegalArgumentException(
              "Runtime '%s' is incompatible with language '%s'".formatted(runtime, language));
        }
        return candidate;
      }
    }
    throw new IllegalArgumentException(
        "Unsupported managed-script language '%s'".formatted(language));
  }
}
