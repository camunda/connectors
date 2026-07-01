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

import io.camunda.connector.runtime.core.secret.SecretFilter;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * A {@link SecretFilter} that resolves the allowed secret names on the first {@link
 * #isAllowed(String)} call rather than at construction time. This defers the BPMN process
 * definition lookup to the point where secrets are actually being replaced, so that any exception
 * thrown by the supplier propagates through the normal connector error-handling path and results in
 * a proper Zeebe {@code failJob} command.
 *
 * <p>The supplier is called exactly once per filter instance regardless of outcome. When the
 * supplier returns {@code null} (LAX mode: lookup failed, fall back to allow-all), all subsequent
 * calls to {@link #isAllowed(String)} return {@code true} without re-invoking the supplier.
 */
public class LazyLoadingSecretFilter implements SecretFilter {
  private final Supplier<List<String>> secretNamesSupplier;

  private volatile boolean initialized = false;
  private Set<String> secretNames;

  public LazyLoadingSecretFilter(Supplier<List<String>> secretNamesSupplier) {
    this.secretNamesSupplier = secretNamesSupplier;
  }

  @Override
  public boolean isAllowed(String secretName) {
    if (!initialized) {
      synchronized (this) {
        if (!initialized) {
          List<String> names = secretNamesSupplier.get();
          secretNames = names != null ? Set.copyOf(names) : null;
          initialized = true;
        }
      }
    }
    if (secretNames != null) {
      return secretNames.contains(secretName);
    } else {
      return true;
    }
  }
}
