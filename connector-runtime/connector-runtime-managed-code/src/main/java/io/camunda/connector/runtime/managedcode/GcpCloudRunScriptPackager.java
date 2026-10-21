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
import java.util.Locale;
import java.util.Map;

/**
 * Packages a script for Google Cloud Run functions: the user script next to a generated Functions
 * Framework entrypoint that receives the contract request as the HTTP body and returns the contract
 * response.
 */
final class GcpCloudRunScriptPackager {

  static final String ENTRYPOINT = "managed_script";

  private static final byte[] PYTHON_MAIN =
      ScriptPackage.resource(GcpCloudRunScriptPackager.class, "/managed-code-gcp/main.py");
  private static final byte[] PYTHON_REQUIREMENTS =
      ScriptPackage.resource(GcpCloudRunScriptPackager.class, "/managed-code-gcp/requirements.txt");
  private static final byte[] NODE_INDEX =
      ScriptPackage.resource(GcpCloudRunScriptPackager.class, "/managed-code-gcp/index.mjs");
  private static final byte[] NODE_PACKAGE =
      ScriptPackage.resource(GcpCloudRunScriptPackager.class, "/managed-code-gcp/package.json");
  private static final String BUILD_VERSION =
      ScriptPackage.sha256(PYTHON_MAIN, PYTHON_REQUIREMENTS, NODE_INDEX, NODE_PACKAGE);

  /** Hash of the generated entrypoints, part of the artifact digest. */
  String buildVersion() {
    return BUILD_VERSION;
  }

  ScriptPackage packageFor(ArtifactSpec artifact) {
    if (artifact.dependencies().isPresent()) {
      throw new ProvisioningException(
          "UNSUPPORTED_DEPENDENCIES",
          "Dependencies are not supported on Google Cloud Run functions",
          false);
    }
    final Map<String, byte[]> files = new LinkedHashMap<>();
    return switch (artifact.language()) {
      case PYTHON -> {
        // The pinned Functions Framework is the platform's, not a dependency of the script.
        files.put("main.py", PYTHON_MAIN);
        files.put("requirements.txt", PYTHON_REQUIREMENTS);
        files.put("user_script.py", artifact.script());
        yield new ScriptPackage(files, ENTRYPOINT, cloudRunRuntime(artifact));
      }
      case JAVASCRIPT -> {
        files.put("index.mjs", NODE_INDEX);
        files.put("package.json", NODE_PACKAGE);
        files.put("user_script.mjs", artifact.script());
        yield new ScriptPackage(files, ENTRYPOINT, cloudRunRuntime(artifact));
      }
    };
  }

  static String cloudRunRuntime(ArtifactSpec artifact) {
    final var runtime = artifact.runtime().toLowerCase(Locale.ROOT);
    return switch (artifact.language()) {
      case PYTHON ->
          switch (runtime) {
            case "python3", "python3.13", "python313" -> "python313";
            case "python3.12", "python312" -> "python312";
            default -> throw unsupportedRuntime(artifact);
          };
      case JAVASCRIPT ->
          switch (runtime) {
            case "nodejs", "nodejs22", "nodejs22.x" -> "nodejs22";
            case "nodejs20", "nodejs20.x" -> "nodejs20";
            default -> throw unsupportedRuntime(artifact);
          };
    };
  }

  private static ProvisioningException unsupportedRuntime(ArtifactSpec artifact) {
    return new ProvisioningException(
        "UNSUPPORTED_RUNTIME",
        "Runtime '%s' is not supported on Google Cloud Run functions".formatted(artifact.runtime()),
        false);
  }
}
