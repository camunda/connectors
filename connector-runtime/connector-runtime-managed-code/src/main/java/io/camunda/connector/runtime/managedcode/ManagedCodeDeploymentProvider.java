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

/** Deployment boundary implemented by provider-specific modules. */
public interface ManagedCodeDeploymentProvider {

  String provider();

  /**
   * Starts an idempotent provider operation and returns its durable identifier.
   *
   * <p>Implementations must derive provider identity deterministically so retrying this call after
   * a crash returns the same operation instead of creating another deployment.
   */
  String startDeployment(ManagedScriptDeployment deployment) throws Exception;

  /**
   * Resumes or waits for a previously started provider operation.
   *
   * <p>The operation identifier is checkpointed in the control plane before this method is called.
   */
  ManagedCodeDeploymentResult resumeDeployment(
      ManagedScriptDeployment deployment, String providerOperationId) throws Exception;
}
