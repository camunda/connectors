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
package io.camunda.connector.test.inbound;

import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorResult;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.impl.context.AbstractConnectorContext;
import io.camunda.connector.impl.inbound.InboundConnectorProperties;
import io.camunda.connector.impl.inbound.correlation.MessageCorrelationPoint;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Test helper class for creating an {@link InboundConnectorContext} with a fluent API. */
public class InboundConnectorContextBuilder {

  protected final Map<String, String> secrets = new HashMap<>();
  protected SecretProvider secretProvider = secrets::get;
  protected InboundConnectorProperties properties;
  protected Object propertiesAsType;
  protected InboundConnectorResult<?> result;
  protected ValidationProvider validationProvider;

  public static InboundConnectorContextBuilder create() {
    return new InboundConnectorContextBuilder();
  }

  /**
   * Provides the secret's value for the given name.
   *
   * @param name - the secret's name, e.g. MY_SECRET when referred to as "secrets.MY_SECRET"
   * @param value - the secret's value
   * @return builder for fluent API
   */
  public InboundConnectorContextBuilder secret(String name, String value) {
    secrets.put(name, value);
    return this;
  }

  /**
   * Provides the secret values via the defined {@link SecretProvider}.
   *
   * @param secretProvider - provider for secret values, given a secret name
   * @return builder for fluent API
   */
  public InboundConnectorContextBuilder secrets(SecretProvider secretProvider) {
    this.secretProvider = secretProvider;
    return this;
  }

  /**
   * Provides the property value for the given name.
   *
   * @param key - property name
   * @param value - property value
   * @return builder for fluent API
   */
  public InboundConnectorContextBuilder property(String key, String value) {
    if (properties == null) {
      properties = getGenericProperties();
    }
    properties.getProperties().put(key, value);
    return this;
  }

  /**
   * Provides multiple properties
   *
   * @param properties - new properties
   * @return builder for fluent API
   */
  public InboundConnectorContextBuilder properties(InboundConnectorProperties properties) {
    assertNoProperties();
    this.properties = properties;
    return this;
  }

  /**
   * Provides multiple properties using a {@link InboundConnectorPropertiesBuilder}
   *
   * @param properties - new properties
   * @return builder for fluent API
   */
  public InboundConnectorContextBuilder properties(InboundConnectorPropertiesBuilder properties) {
    assertNoProperties();
    this.properties = properties.build();
    return this;
  }

  /**
   * Provides properties as object
   *
   * @param obj - properties as object
   * @return builder for fluent API
   */
  public InboundConnectorContextBuilder properties(Object obj) {
    assertNoProperties();
    this.propertiesAsType = obj;
    return this;
  }

  /**
   * Assigns correlation result that will be returned on {@link InboundConnectorContext#correlate}
   * call
   *
   * @param result - correlation result
   * @return builder for fluent API
   */
  public InboundConnectorContextBuilder result(InboundConnectorResult<?> result) {
    this.result = result;
    return this;
  }

  public InboundConnectorContextBuilder validation(ValidationProvider validationProvider) {
    this.validationProvider = validationProvider;
    return this;
  }

  /**
   * @return the {@link io.camunda.connector.api.inbound.InboundConnectorContext} including all
   *     previously defined properties
   */
  public TestInboundConnectorContext build() {
    return new TestInboundConnectorContext(secretProvider);
  }

  private void assertNoProperties() {

    if (propertiesAsType != null) {
      throw new IllegalStateException("propertiesAsType already set");
    }

    if (properties != null) {
      throw new IllegalStateException("propertiesAsType already set");
    }
  }

  private InboundConnectorProperties getGenericProperties() {
    return new InboundConnectorProperties(
        new MessageCorrelationPoint("msgName"),
        new HashMap<>(),
        UUID.randomUUID().toString(),
        0,
        0);
  }

  public class TestInboundConnectorContext extends AbstractConnectorContext
      implements InboundConnectorContext {

    private final List<Object> correlatedEvents = new ArrayList<>();

    protected TestInboundConnectorContext(SecretProvider secretProvider) {
      super(secretProvider);
    }

    @Override
    public InboundConnectorResult<?> correlate(Object variables) {
      if (result == null) {
        throw new IllegalStateException("Mock result not provided during test context creation");
      }
      correlatedEvents.add(variables);
      return result;
    }

    @Override
    public void cancel(Throwable exception) {
      // do nothing
    }

    @Override
    public InboundConnectorProperties getProperties() {
      return properties;
    }

    @Override
    public <T> T getPropertiesAsType(Class<T> cls) {
      if (propertiesAsType == null) {
        throw new IllegalStateException("propertiesAsType not provided");
      }

      try {
        return cls.cast(propertiesAsType);
      } catch (ClassCastException ex) {
        throw new IllegalStateException(
            "no propertiesAsType of type " + cls.getName() + " provided", ex);
      }
    }

    @Override
    public ValidationProvider getValidationProvider() {
      return Optional.ofNullable(validationProvider).orElseGet(super::getValidationProvider);
    }

    public List<Object> getCorrelations() {
      return correlatedEvents;
    }
  }
}
