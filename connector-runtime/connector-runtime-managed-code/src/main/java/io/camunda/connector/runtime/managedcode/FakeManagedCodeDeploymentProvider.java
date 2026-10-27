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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deterministic local provider enabled only with {@code
 * camunda.connector.managed-code.provider=fake}.
 */
public final class FakeManagedCodeDeploymentProvider implements ManagedCodeDeploymentProvider {

  private final Map<String, ManagedCodeDeploymentResult> deployments = new ConcurrentHashMap<>();

  @Override
  public String provider() {
    return "fake";
  }

  @Override
  public String startDeployment(ManagedScriptDeployment deployment) {
    return operationId(deployment);
  }

  @Override
  public ManagedCodeDeploymentResult resumeDeployment(
      ManagedScriptDeployment deployment, String providerOperationId) {
    if (!operationId(deployment).equals(providerOperationId)) {
      throw new IllegalArgumentException(
          "Unknown fake provider operation '%s'".formatted(providerOperationId));
    }
    return deployments.computeIfAbsent(deployment.deploymentId(), ignored -> result(deployment));
  }

  public Map<String, ManagedCodeDeploymentResult> deployments() {
    return Map.copyOf(deployments);
  }

  private static ManagedCodeDeploymentResult result(ManagedScriptDeployment deployment) {
    var operationId = operationId(deployment);
    return new ManagedCodeDeploymentResult(
        "fake-deployment-" + operationId.substring("fake-operation-".length()),
        Map.of("artifactSha256", operationId.substring("fake-operation-".length())));
  }

  private static String operationId(ManagedScriptDeployment deployment) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      digest.update(deployment.tenantId().getBytes(StandardCharsets.UTF_8));
      digest.update((byte) 0);
      digest.update(deployment.provider().getBytes(StandardCharsets.UTF_8));
      digest.update((byte) 0);
      digest.update(deployment.artifactDigest());
      var hash = HexFormat.of().formatHex(digest.digest());
      return "fake-operation-" + hash;
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }
}
