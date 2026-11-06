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

import java.util.List;

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
   * Makes sure the executor of the language exists, runs the wrapper of this runtime and has the
   * configured settings, and returns once it can be invoked. A provider whose executors are
   * deployed separately checks it instead of creating it.
   *
   * <p>The connector calls it in the background once the runtime is ready, from the first job that
   * needs the executor, and again after {@link #execute} reported {@link
   * ErrorCode#DEPLOYMENT_MISSING}, before it invokes the executor again (ADR-0008). It may
   * therefore run on any thread, more than once in the life of a runtime, and for different
   * languages at once. Must be idempotent: several runtime instances may call it at once.
   *
   * @throws ProvisioningException when the executor cannot be made ready: its code tells whether
   *     the connector tries again ({@link ErrorCode#retryable()}), and {@link
   *     ProvisioningException#permissionDenied()} marks a denial that a new cluster's grants may
   *     still lift
   */
  void ensureExecutors(ScriptLanguage language);

  /**
   * Runs the script of the request on the executor of its language. Script and platform failures
   * are returned as a {@code FAILED} response, not thrown. A missing executor is reported with
   * {@link ErrorCode#DEPLOYMENT_MISSING}.
   *
   * @throws ProvisioningException only for {@link ProvisioningException#permissionDenied}, when the
   *     platform denies the invocation: the job keeps its retries while the grants of a new cluster
   *     propagate, and fails without retries after
   */
  ExecutionResponse execute(ScriptLanguage language, ExecutionRequest request);

  /**
   * The executors this runtime may call: for each language, the executor of its own wrapper, the
   * only one it calls (ADR-0008). Computed from the wrapper and the configuration, without calling
   * the platform, so it answers whether or not the executors exist yet.
   *
   * <p>Controller, or the operator, deletes the functions under the prefix that no runtime instance
   * reports, so a provider that overrides it reports every executor it may call. The default throws
   * rather than return an empty list, which would have every executor of the cluster deleted: the
   * endpoint that reports the executors then answers that it cannot, and nothing is deleted.
   *
   * @throws UnsupportedOperationException when the provider does not report its executors
   */
  default List<ExecutorReference> executors() {
    throw new UnsupportedOperationException(
        "The managed-code provider '%s' does not report its executors".formatted(name()));
  }
}
