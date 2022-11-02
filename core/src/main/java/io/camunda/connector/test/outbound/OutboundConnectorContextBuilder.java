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
package io.camunda.connector.test.outbound;

import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.api.secret.SecretStore;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.impl.context.AbstractConnectorContext;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Test helper class for creating a {@link OutboundConnectorContext} with a fluent API. */
public class OutboundConnectorContextBuilder {

  protected final Map<String, String> secrets = new HashMap<>();
  protected SecretProvider secretProvider = secrets::get;
  protected SecretStore secretStore = new SecretStore(secretProvider);

  protected ValidationProvider validationProvider;

  protected String variablesAsJSON;

  private Object variablesAsObject;

  /**
   * @return a new instance of the {@link OutboundConnectorContextBuilder}
   */
  public static OutboundConnectorContextBuilder create() {
    return new OutboundConnectorContextBuilder();
  }

  private void assertNoVariables() {

    if (this.variablesAsJSON != null) {
      throw new IllegalStateException("variablesAsJSON already set");
    }

    if (this.variablesAsObject != null) {
      throw new IllegalStateException("variablesAsObject already set");
    }
  }

  /**
   * Provides the variables as a JSON string.
   *
   * @param variablesAsJSON - the variables as JSON
   * @return builder for fluent API
   */
  public OutboundConnectorContextBuilder variables(String variablesAsJSON) {
    this.assertNoVariables();

    this.variablesAsJSON = variablesAsJSON;
    return this;
  }

  /**
   * Provides the variables as an object.
   *
   * @param variablesAsObject - the variables as a mapped object
   * @return builder for fluent API
   */
  public OutboundConnectorContextBuilder variables(Object variablesAsObject) {
    this.assertNoVariables();

    this.variablesAsObject = variablesAsObject;
    return this;
  }

  /**
   * Provides the secret's value for the given name.
   *
   * @param name - the secret's name, e.g. MY_SECRET when referred to as "secrets.MY_SECRET"
   * @param value - the secret's value
   * @return builder for fluent API
   */
  public OutboundConnectorContextBuilder secret(String name, String value) {
    secrets.put(name, value);
    return this;
  }

  /**
   * Provides the secret values via the defined {@link SecretProvider}.
   *
   * @param secretProvider - provider for secret values, given a secret name
   * @return builder for fluent API
   */
  public OutboundConnectorContextBuilder secrets(SecretProvider secretProvider) {
    this.secretProvider = secretProvider;
    return this;
  }

  /**
   * Provides the secret values via the defined {@link SecretStore}.
   *
   * @param secretStore - secret store
   * @return builder for fluent API
   */
  public OutboundConnectorContextBuilder secrets(SecretStore secretStore) {
    this.secretStore = secretStore;
    return this;
  }

  public OutboundConnectorContextBuilder validation(ValidationProvider validationProvider) {
    this.validationProvider = validationProvider;
    return this;
  }

  /**
   * @return the {@link OutboundConnectorContext} including all previously defined properties
   */
  public TestConnectorContext build() {
    return new TestConnectorContext(secretStore);
  }

  public class TestConnectorContext extends AbstractConnectorContext
      implements OutboundConnectorContext {

    protected TestConnectorContext(SecretStore secretStore) {
      super(secretStore);
    }

    @Override
    public String getVariables() {

      if (variablesAsJSON == null) {
        throw new IllegalStateException("variablesAsJSON not provided");
      }

      return variablesAsJSON;
    }

    @Override
    public <T extends Object> T getVariablesAsType(Class<T> cls) {

      if (variablesAsObject == null) {
        throw new IllegalStateException("variablesAsObject not provided");
      }

      try {
        return cls.cast(variablesAsObject);
      } catch (ClassCastException ex) {
        throw new IllegalStateException(
            "no variablesAsObject of type " + cls.getName() + " provided", ex);
      }
    }

    @Override
    public ValidationProvider getValidationProvider() {
      return Optional.ofNullable(validationProvider).orElseGet(super::getValidationProvider);
    }
  }
}
