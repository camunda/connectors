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
import io.camunda.connector.api.validation.ValidationUtil;
import io.camunda.connector.runtime.core.secret.SecretHandler;

public abstract class AbstractConnectorContext {

  protected SecretHandler secretHandler;
  protected final SecretProvider secretProvider;

  protected final ValidationProvider validationProvider;

  protected AbstractConnectorContext(
      final SecretProvider secretProvider, final ValidationProvider validationProvider) {
    if (secretProvider == null) {
      throw new RuntimeException("Secret provider required in Connector context but was null");
    }
    this.secretProvider = secretProvider;

    if (validationProvider == null) {
      this.validationProvider = ValidationUtil.discoverDefaultValidationProviderImplementation();
    } else {
      this.validationProvider = validationProvider;
    }
  }

  public void replaceSecrets(final Object input) {
    getSecretHandler().handleSecretContainer(input, getSecretHandler());
  }

  public SecretHandler getSecretHandler() {
    if (secretHandler == null) {
      secretHandler = new SecretHandler(secretProvider);
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
