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
import io.camunda.connector.runtime.core.secret.SecretFilter.Secret;
import java.util.List;
import java.util.function.Supplier;

/**
 * A {@link SecretFilter} that resolves the allowed secrets on the first {@link #isAllowed(Secret)}
 * call rather than at construction time.
 */
public class LazyLoadingSecretFilter implements SecretFilter {
  private final Supplier<List<Secret>> secretsSupplier;

  private volatile boolean initialized;
  private SecretFilter delegate;
  private Throwable initializationFailure;

  public LazyLoadingSecretFilter(Supplier<List<Secret>> secretsSupplier) {
    this.secretsSupplier = secretsSupplier;
  }

  @Override
  public boolean isAllowed(Secret secret) {
    if (!initialized) {
      synchronized (this) {
        if (!initialized) {
          try {
            List<Secret> secrets = secretsSupplier.get();
            delegate = secrets != null ? SecretFilter.allowOnly(secrets) : SecretFilter.allowAll();
          } catch (Throwable e) {
            initializationFailure = e;
            initialized = true;
            throw e;
          }
          initialized = true;
        }
      }
    }
    if (initializationFailure instanceof RuntimeException runtimeException) {
      throw runtimeException;
    }
    if (initializationFailure instanceof Error error) {
      throw error;
    }
    return delegate.isAllowed(secret);
  }
}
