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
package io.camunda.connector.managedscript.api;

/**
 * Supported managed-script languages. The language of a script follows the extension of its file
 * name, and nothing else: each extra language is one more executor per cluster.
 */
public enum ScriptLanguage {
  PYTHON("python", ".py"),
  JAVASCRIPT("javascript", ".js");

  private final String language;
  private final String extension;

  ScriptLanguage(String language, String extension) {
    this.language = language;
    this.extension = extension;
  }

  public String language() {
    return language;
  }

  public String extension() {
    return extension;
  }
}
