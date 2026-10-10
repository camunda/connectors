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

/**
 * Provider SPI for managed-script execution: provisions an artifact on first use and invokes it.
 *
 * <p>Implementations must make {@link #ensureProvisioned(ArtifactSpec)} idempotent for {@link
 * ArtifactSpec#deploymentName()}: the name is deterministic, creation is create-if-absent, and an
 * existing deployment with that name is adopted instead of being created again. Several runtime
 * instances may call it concurrently for the same artifact.
 */
public interface ManagedCodeProvider {

  /** Stable provider name, part of the deployment registry key. */
  String name();

  /**
   * Builds and creates the provider deployment if absent, or adopts an existing one. The call
   * returns once the provider accepted the work; {@link ProvisioningHandle#ready()} completes when
   * the deployment can be invoked.
   *
   * @throws ProvisioningException when the artifact cannot be built or deployed
   */
  ProvisioningHandle ensureProvisioned(ArtifactSpec artifact);

  /**
   * Invokes a ready deployment with an execution contract request. Script and platform failures are
   * returned as a {@code FAILED} response, not thrown. A deployment that no longer exists is
   * reported with {@link ExecutionResponse#DEPLOYMENT_MISSING}.
   */
  ExecutionResponse invoke(ProviderDeployment deployment, ExecutionRequest request);

  /**
   * Deletes a provider deployment. Must be idempotent: a deployment that does not exist counts as
   * deleted. Intended for the eviction of unused deployments, which the worker does not run yet.
   */
  void delete(ProviderDeployment deployment);
}
