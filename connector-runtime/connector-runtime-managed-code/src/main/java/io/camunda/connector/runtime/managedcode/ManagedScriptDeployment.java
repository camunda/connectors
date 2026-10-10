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

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable desired deployment acquired from the managed-script control plane.
 *
 * @param deploymentId stable reconciliation item identifier
 * @param definitionRevision revision used to fence lifecycle updates
 * @param leaseToken fencing token returned by the control plane
 * @param provider target deployment provider
 * @param tenantId tenant that owns the managed script
 * @param resourceKey immutable Camunda resource key
 * @param resourceName provider-neutral resource name
 * @param artifactDigest immutable desired-artifact identity
 * @param language script language
 * @param runtime requested runtime
 * @param artifact immutable script artifact
 * @param existingProviderOperationId persisted provider operation identifier
 */
public record ManagedScriptDeployment(
    String deploymentId,
    long definitionRevision,
    String leaseToken,
    String provider,
    String tenantId,
    long resourceKey,
    String resourceName,
    byte[] artifactDigest,
    String language,
    String runtime,
    byte[] artifact,
    Optional<String> existingProviderOperationId) {

  public ManagedScriptDeployment {
    requireNonBlank(deploymentId, "deploymentId");
    if (definitionRevision < 0) {
      throw new IllegalArgumentException("definitionRevision must not be negative");
    }
    requireNonBlank(leaseToken, "leaseToken");
    requireNonBlank(provider, "provider");
    requireNonBlank(tenantId, "tenantId");
    if (resourceKey <= 0) {
      throw new IllegalArgumentException("resourceKey must be positive");
    }
    requireNonBlank(resourceName, "resourceName");
    artifactDigest = Objects.requireNonNull(artifactDigest, "artifactDigest").clone();
    if (artifactDigest.length == 0) {
      throw new IllegalArgumentException("artifactDigest must not be empty");
    }
    requireNonBlank(language, "language");
    requireNonBlank(runtime, "runtime");
    artifact = Objects.requireNonNull(artifact, "artifact").clone();
    existingProviderOperationId =
        Objects.requireNonNull(existingProviderOperationId, "existingProviderOperationId");
  }

  @Override
  public byte[] artifactDigest() {
    return artifactDigest.clone();
  }

  @Override
  public byte[] artifact() {
    return artifact.clone();
  }

  public ManagedScriptDeployment withProviderOperation(
      long updatedRevision, String providerOperationId) {
    return new ManagedScriptDeployment(
        deploymentId,
        updatedRevision,
        leaseToken,
        provider,
        tenantId,
        resourceKey,
        resourceName,
        artifactDigest,
        language,
        runtime,
        artifact,
        Optional.of(providerOperationId));
  }

  private static void requireNonBlank(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
  }
}
