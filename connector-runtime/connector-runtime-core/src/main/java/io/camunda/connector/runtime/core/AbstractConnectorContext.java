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
import io.camunda.connector.runtime.core.secret.SecretResolverMode;
import org.jspecify.annotations.Nullable;

public abstract class AbstractConnectorContext {

  protected @Nullable SecretHandler secretHandler;
  protected final SecretProvider secretProvider;
  protected final SecretFilter secretFilter;

  protected final ValidationProvider validationProvider;

  private final SecretResolverMode secretResolverMode;

  protected AbstractConnectorContext(
      final SecretProvider secretProvider,
      final SecretFilter secretFilter,
      final ValidationProvider validationProvider) {
    this(secretProvider, secretFilter, validationProvider, SecretResolverMode.ALL);
  }

  protected AbstractConnectorContext(
      final SecretProvider secretProvider,
      final SecretFilter secretFilter,
      final ValidationProvider validationProvider,
      final SecretResolverMode secretResolverMode) {
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
    this.secretResolverMode =
        secretResolverMode != null ? secretResolverMode : SecretResolverMode.ALL;
  }

  public SecretHandler getSecretHandler() {
    if (secretHandler == null) {
      secretHandler = new SecretHandler(secretProvider, secretFilter, secretResolverMode);
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
