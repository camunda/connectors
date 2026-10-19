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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Packages a script for the local provider: the user script next to a wrapper that reads the
 * request from {@code request.json} and writes the response to standard output.
 */
public final class LocalScriptPackager implements ScriptPackager {

  private static final byte[] PYTHON_WRAPPER =
      ScriptPackager.resource(LocalScriptPackager.class, "/managed-code-local/python_wrapper.py");
  private static final byte[] NODE_WRAPPER =
      ScriptPackager.resource(LocalScriptPackager.class, "/managed-code-local/node_wrapper.mjs");
  private static final String BUILD_VERSION = ScriptPackager.sha256(PYTHON_WRAPPER, NODE_WRAPPER);

  @Override
  public String provider() {
    return LocalManagedCodeProvider.NAME;
  }

  @Override
  public String buildVersion() {
    return BUILD_VERSION;
  }

  @Override
  public ScriptPackage packageFor(ArtifactSpec artifact) {
    return new ScriptPackage(
        files(artifact.language(), artifact.script()),
        entrypoint(artifact.language()),
        artifact.runtime());
  }

  /** The files of a local invocation, also used to run a script without a deployment. */
  static Map<String, byte[]> files(ScriptLanguage language, byte[] script) {
    final Map<String, byte[]> files = new LinkedHashMap<>();
    files.put(scriptFileName(language), script);
    files.put(
        entrypoint(language), language == ScriptLanguage.PYTHON ? PYTHON_WRAPPER : NODE_WRAPPER);
    return files;
  }

  static String entrypoint(ScriptLanguage language) {
    return language == ScriptLanguage.PYTHON ? "python_wrapper.py" : "node_wrapper.mjs";
  }

  static String scriptFileName(ScriptLanguage language) {
    return language == ScriptLanguage.PYTHON ? "user_script.py" : "user_script.mjs";
  }
}
