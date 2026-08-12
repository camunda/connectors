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
package io.camunda.connector.runtime.core;

import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.runtime.core.secret.SecretFilter;
import io.camunda.connector.runtime.core.secret.SecretHandler;
import io.camunda.connector.runtime.core.secret.SecretReferenceResolver;
import org.jspecify.annotations.Nullable;

public abstract class AbstractConnectorContext {

  protected @Nullable SecretHandler secretHandler;
  protected final SecretProvider secretProvider;
  protected final SecretFilter secretFilter;
  protected final SecretReferenceResolver referenceResolver;

  protected final ValidationProvider validationProvider;

  /**
   * Kept so subclasses that only pass three arguments keep compiling. Defaults the {@code
   * camunda.secrets.<name>} resolver to {@link SecretReferenceResolver#noop()}, which is why {@code
   * JobHandlerContext} (the only caller of this overload) is unaffected by this change.
   */
  protected AbstractConnectorContext(
      final SecretProvider secretProvider,
      SecretFilter secretFilter,
      final ValidationProvider validationProvider) {
    this(secretProvider, secretFilter, validationProvider, SecretReferenceResolver.noop());
  }

  protected AbstractConnectorContext(
      final SecretProvider secretProvider,
      SecretFilter secretFilter,
      final ValidationProvider validationProvider,
      final SecretReferenceResolver referenceResolver) {
    if (secretFilter == null) {
      throw new IllegalArgumentException(
          "Secret filter required in Connector context but was null");
    }
    this.secretFilter = secretFilter;
    if (secretProvider == null) {
      throw new RuntimeException("Secret provider required in Connector context but was null");
    }
    this.secretProvider = secretProvider;

    if (validationProvider == null) {
      throw new RuntimeException("Validation provider required in Connector context but was null");
    }
    this.validationProvider = validationProvider;
    this.referenceResolver =
        referenceResolver != null ? referenceResolver : SecretReferenceResolver.noop();
  }

  public SecretHandler getSecretHandler() {
    if (secretHandler == null) {
      secretHandler = new SecretHandler(secretProvider, secretFilter, referenceResolver);
    }
    return secretHandler;
  }

  public void validate(Object input) {
    validationProvider.validate(input);
  }

  /**
   * Override this method to provide your own {@link ValidationProvider} discovery strategy. By
   * default, SPI is being used and should be implemented by each implementation.
   *
   * @return the desired validation provider implementation
   */
  protected ValidationProvider getValidationProvider() {
    return validationProvider;
  }
}
