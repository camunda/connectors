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
 * Provider SPI for managed-script execution through generic executors: one trusted function per
 * language and cluster, with no user code in it, that runs every script it receives in a new child
 * process. Scripts are sent as data with their input, so a script costs no control-plane call.
 *
 * <p>Not a stable API yet: it may change in any release (ADR-0008).
 */
public interface ManagedCodeProvider {

  /** Stable provider name, the value of {@code camunda.connector.managed-code.provider}. */
  String name();

  /**
   * Makes sure the executors of the cluster exist, run the wrapper of this runtime and have the
   * configured settings, and returns once they can be invoked. A provider whose executors are
   * deployed separately checks them instead of creating them.
   *
   * <p>The connector may call it in the background once the runtime is ready, from the first job
   * that needs the executors, and again after {@link #execute} reported {@link
   * ErrorCode#DEPLOYMENT_MISSING}, before it invokes the executor again (ADR-0008). It may
   * therefore run on any thread and more than once in the life of a runtime. Must be idempotent:
   * several runtime instances may call it at once.
   *
   * @throws ProvisioningException when the executors cannot be made ready
   */
  void ensureExecutors();

  /**
   * Runs the script of the request on the executor of its language. Script and platform failures
   * are returned as a {@code FAILED} response, not thrown. A missing executor is reported with
   * {@link ErrorCode#DEPLOYMENT_MISSING}.
   */
  ExecutionResponse execute(ScriptLanguage language, ExecutionRequest request);
}
