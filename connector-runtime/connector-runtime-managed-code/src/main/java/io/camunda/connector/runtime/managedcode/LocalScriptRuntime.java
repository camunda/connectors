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

enum LocalScriptRuntime {
  PYTHON("python", ".py", "python_wrapper.py", List.of("python3", "python"), 3, 10),
  JAVASCRIPT("javascript", ".js", "node_wrapper.mjs", List.of("node"), 20, 0);

  private final String language;
  private final String extension;
  private final String wrapperResource;
  private final List<String> executableNames;
  private final int minimumMajor;
  private final int minimumMinor;

  LocalScriptRuntime(
      String language,
      String extension,
      String wrapperResource,
      List<String> executableNames,
      int minimumMajor,
      int minimumMinor) {
    this.language = language;
    this.extension = extension;
    this.wrapperResource = wrapperResource;
    this.executableNames = executableNames;
    this.minimumMajor = minimumMajor;
    this.minimumMinor = minimumMinor;
  }

  String language() {
    return language;
  }

  String extension() {
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

  static LocalScriptRuntime from(String language, String runtime, String resourceName) {
    for (var candidate : values()) {
      if (candidate.language.equalsIgnoreCase(language)) {
        if (!resourceName.endsWith(candidate.extension)) {
          throw new IllegalArgumentException(
              "%s scripts must use the %s extension"
                  .formatted(candidate.language, candidate.extension));
        }
        if (!runtime.toLowerCase().startsWith(candidate == PYTHON ? "python" : "node")) {
          throw new IllegalArgumentException(
              "Runtime '%s' is incompatible with language '%s'".formatted(runtime, language));
        }
        return candidate;
      }
    }
    throw new IllegalArgumentException(
        "Unsupported local managed-script language '%s'".formatted(language));
  }
}
