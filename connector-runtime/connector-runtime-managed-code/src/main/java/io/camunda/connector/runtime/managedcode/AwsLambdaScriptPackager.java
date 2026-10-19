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
 * Packages a script for AWS Lambda: the user script next to a generated handler that receives the
 * contract request as the Lambda event and returns the contract response.
 */
public final class AwsLambdaScriptPackager implements ScriptPackager {

  private static final byte[] PYTHON_HANDLER =
      ScriptPackager.resource(AwsLambdaScriptPackager.class, "/managed-code-aws/python_handler.py");
  private static final byte[] NODE_HANDLER =
      ScriptPackager.resource(AwsLambdaScriptPackager.class, "/managed-code-aws/node_handler.mjs");
  private static final String BUILD_VERSION = ScriptPackager.sha256(PYTHON_HANDLER, NODE_HANDLER);

  @Override
  public String provider() {
    return AwsLambdaManagedCodeProvider.NAME;
  }

  @Override
  public String buildVersion() {
    return BUILD_VERSION;
  }

  @Override
  public ScriptPackage packageFor(ArtifactSpec artifact) {
    if (artifact.dependencies().isPresent()) {
      throw new ProvisioningException(
          "UNSUPPORTED_DEPENDENCIES", "Dependencies are not supported on AWS Lambda", false);
    }
    final Map<String, byte[]> files = new LinkedHashMap<>();
    return switch (artifact.language()) {
      case PYTHON -> {
        files.put("handler.py", PYTHON_HANDLER);
        files.put("user_script.py", artifact.script());
        yield new ScriptPackage(files, "handler.lambda_handler", lambdaRuntime(artifact));
      }
      case JAVASCRIPT -> {
        files.put("index.mjs", NODE_HANDLER);
        files.put("user_script.mjs", artifact.script());
        yield new ScriptPackage(files, "index.handler", lambdaRuntime(artifact));
      }
    };
  }

  static String lambdaRuntime(ArtifactSpec artifact) {
    final var runtime = artifact.runtime().toLowerCase(Locale.ROOT);
    return switch (artifact.language()) {
      case PYTHON ->
          switch (runtime) {
            case "python3", "python3.13" -> "python3.13";
            case "python3.12" -> "python3.12";
            default -> throw unsupportedRuntime(artifact);
          };
      case JAVASCRIPT ->
          switch (runtime) {
            case "nodejs", "nodejs22", "nodejs22.x" -> "nodejs22.x";
            case "nodejs20", "nodejs20.x" -> "nodejs20.x";
            default -> throw unsupportedRuntime(artifact);
          };
    };
  }

  private static ProvisioningException unsupportedRuntime(ArtifactSpec artifact) {
    return new ProvisioningException(
        "UNSUPPORTED_RUNTIME",
        "Runtime '%s' is not supported on AWS Lambda".formatted(artifact.runtime()),
        false);
  }
}
