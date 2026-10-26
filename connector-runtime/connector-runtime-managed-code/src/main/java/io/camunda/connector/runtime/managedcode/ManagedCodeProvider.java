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
 * Provider SPI for managed-script execution through generic executors: one trusted function per
 * language and cluster, with no user code in it, that runs every script it receives in a new child
 * process. Scripts are sent as data with their input, so a script costs no control-plane call.
 *
 * <p>Implementations must make {@link #ensureExecutors()} idempotent: executor names are
 * deterministic, creation is create-if-absent, and an existing executor is adopted. Several runtime
 * instances may call it concurrently.
 */
public interface ManagedCodeProvider {

  /** Stable provider name, the value of {@code camunda.connector.managed-code.provider}. */
  String name();

  /**
   * Creates the executors of the cluster if they are absent and returns once they can be invoked.
   * Called when the runtime starts, and again when an executor is reported missing. A provider
   * whose executors are deployed by an operator keeps the default, which does nothing.
   *
   * @throws ProvisioningException when an executor cannot be created
   */
  default void ensureExecutors() {}

  /**
   * Runs the script of the request on the executor of its language. Script and platform failures
   * are returned as a {@code FAILED} response, not thrown. A missing executor is reported with
   * {@link ExecutionResponse#DEPLOYMENT_MISSING}.
   */
  ExecutionResponse execute(ScriptLanguage language, ExecutionRequest request);
}
