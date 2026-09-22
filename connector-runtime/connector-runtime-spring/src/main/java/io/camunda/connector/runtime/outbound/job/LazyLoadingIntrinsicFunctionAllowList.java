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
package io.camunda.connector.runtime.outbound.job;

import io.camunda.connector.runtime.core.intrinsic.AllowedIntrinsicFunction;
import io.camunda.connector.runtime.core.intrinsic.IntrinsicFunctionAllowList;
import java.util.List;
import java.util.function.Supplier;

/**
 * An {@link IntrinsicFunctionAllowList} that resolves the process model's declared functions on the
 * first {@link #isAllowed(Call)} call rather than at construction time. {@code
 * IntrinsicFunctionUtil}'s bound-tree walk only ever calls {@code isAllowed} when a job's variables
 * actually contain a {@code camunda.function.type} discriminator somewhere, so building the
 * allow-list eagerly at {@link ConfigurableIntrinsicFunctionAllowListFactory#create} time would
 * fetch and parse the deployed BPMN model for every single job, including the overwhelming majority
 * that never use an intrinsic function at all, rather than only the jobs that actually need the
 * check. Deferring the fetch here also means a fetch/parse failure surfaces from {@code isAllowed}
 * — reached from within the connector's own guarded execution — rather than from {@code create()},
 * called before that guard.
 *
 * <p>The supplier is called exactly once per instance regardless of outcome: once resolved (or
 * failed), every subsequent {@link #isAllowed(Call)} call reuses that same outcome without
 * re-invoking the supplier or re-fetching the model.
 */
public class LazyLoadingIntrinsicFunctionAllowList implements IntrinsicFunctionAllowList {
  private final Supplier<List<AllowedIntrinsicFunction>> allowedSupplier;

  private volatile boolean initialized = false;
  private IntrinsicFunctionAllowList delegate;
  private RuntimeException initializationFailure;

  public LazyLoadingIntrinsicFunctionAllowList(
      Supplier<List<AllowedIntrinsicFunction>> allowedSupplier) {
    this.allowedSupplier = allowedSupplier;
  }

  @Override
  public boolean isAllowed(Call call) {
    if (!initialized) {
      synchronized (this) {
        if (!initialized) {
          try {
            delegate = IntrinsicFunctionAllowList.allowOnly(allowedSupplier.get());
          } catch (RuntimeException e) {
            initializationFailure = e;
          } finally {
            initialized = true;
          }
        }
      }
    }
    if (initializationFailure != null) {
      throw initializationFailure;
    }
    return delegate.isAllowed(call);
  }
}
