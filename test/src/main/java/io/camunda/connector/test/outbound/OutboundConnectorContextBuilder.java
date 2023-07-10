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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.impl.context.AbstractConnectorContext;
import java.util.HashMap;
import java.util.Map;

/** Test helper class for creating a {@link OutboundConnectorContext} with a fluent API. */
public class OutboundConnectorContextBuilder {

  protected final Map<String, String> secrets = new HashMap<>();
  protected SecretProvider secretProvider = secrets::get;

  protected ValidationProvider validationProvider;

  protected String variablesAsJson;

  protected final Map<String, String> headers = new HashMap<>();

  private final ObjectMapper objectMapper =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  /**
   * @return a new instance of the {@link OutboundConnectorContextBuilder}
   */
  public static OutboundConnectorContextBuilder create() {
    return new OutboundConnectorContextBuilder();
  }

  private void assertNoVariables() {
    if (this.variablesAsJson != null) {
      throw new IllegalStateException("variablesAsJSON already set");
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
    this.variablesAsJson = variablesAsJSON;
    return this;
  }

  /**
   * Provides the custom header value with given key/value pair
   *
   * @param key - custom header key
   * @param value - custom header key
   * @return builder for fluent API
   */
  public OutboundConnectorContextBuilder header(String key, String value) {
    headers.put(key, value);
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

  public OutboundConnectorContextBuilder validation(ValidationProvider validationProvider) {
    this.validationProvider = validationProvider;
    return this;
  }

  /**
   * @return the {@link OutboundConnectorContext} including all previously defined properties
   */
  public TestConnectorContext build() {
    return new TestConnectorContext(secretProvider, validationProvider);
  }

  public class TestConnectorContext extends AbstractConnectorContext
      implements OutboundConnectorContext {

    protected TestConnectorContext(
        SecretProvider secretProvider, ValidationProvider validationProvider) {
      super(secretProvider, validationProvider);
      variablesAsJson = getSecretHandler().replaceSecrets(variablesAsJson);
    }

    @Override
    public Map<String, String> getCustomHeaders() {
      return headers;
    }

    @Override
    public String getVariables() {
      return variablesAsJson;
    }

    @Override
    public <T> T bindVariables(Class<T> cls) {
      try {
        var mappedObject = objectMapper.readValue(variablesAsJson, cls);
        if (validationProvider != null) {
          getValidationProvider().validate(mappedObject);
        }
        return mappedObject;
      } catch (JsonProcessingException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
