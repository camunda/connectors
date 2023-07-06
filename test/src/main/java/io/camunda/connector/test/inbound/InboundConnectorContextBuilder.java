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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorDefinition;
import io.camunda.connector.api.inbound.InboundConnectorResult;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.impl.context.AbstractConnectorContext;
import io.camunda.connector.impl.inbound.result.MessageCorrelationResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Test helper class for creating an {@link InboundConnectorContext} with a fluent API. */
public class InboundConnectorContextBuilder {

  protected final Map<String, String> secrets = new HashMap<>();
  protected SecretProvider secretProvider = secrets::get;
  protected Map<String, Object> properties;
  protected InboundConnectorDefinition definition;
  protected InboundConnectorResult<?> result = new MessageCorrelationResult("mockMsg", 0);
  protected ValidationProvider validationProvider;

  protected ObjectMapper objectMapper = new ObjectMapper();

  public static InboundConnectorContextBuilder create() {
    return new InboundConnectorContextBuilder();
  }

  /**
   * Provides the connector definition.
   *
   * @param definition - the connector definition
   * @return builder for fluent API
   */
  public InboundConnectorContextBuilder definition(InboundConnectorDefinition definition) {
    this.definition = definition;
    return this;
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
      properties = new HashMap<>();
    }
    properties.put(key, value);
    return this;
  }

  /**
   * Provides multiple properties
   *
   * @param properties - new properties
   * @return builder for fluent API
   */
  @SuppressWarnings("unchecked")
  public InboundConnectorContextBuilder properties(Map<String, ?> properties) {
    if (this.properties != null && !this.properties.equals(properties)) {
      throw new IllegalStateException("Properties already set");
    }
    this.properties = (Map<String, Object>) replaceImmutableMaps(properties);
    return this;
  }

  // this allows to create nested maps in place, like Map.of("a", Map.of("b", "c"))
  @SuppressWarnings("unchecked")
  protected Map<String, ?> replaceImmutableMaps(Map<String, ?> properties) {
    Map<String, Object> mutableProperties = new HashMap<>();
    for (Map.Entry<String, ?> entry : properties.entrySet()) {
      Object value = entry.getValue();
      if (value instanceof Map) {
        value = replaceImmutableMaps((Map<String, ?>) value);
      }
      mutableProperties.put(entry.getKey(), value);
    }
    return mutableProperties;
  }

  /**
   * Provides multiple properties as object. The properties will then be converted to an
   * intermediate Map representation.
   *
   * @param properties - new properties
   * @return builder for fluent API
   */
  public InboundConnectorContextBuilder properties(Object properties) {
    if (this.properties != null && !this.properties.equals(properties)) {
      throw new IllegalStateException("Properties already set");
    }
    this.properties = objectMapper.convertValue(properties, new TypeReference<>() {});
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
   * Sets a custom {@link ObjectMapper} that is used to serialize and deserialize the properties. If
   * not provided, default mapper will be used.
   *
   * @param objectMapper - custom object mapper
   * @return builder for fluent API
   */
  public InboundConnectorContextBuilder objectMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    return this;
  }

  /**
   * @return the {@link io.camunda.connector.api.inbound.InboundConnectorContext} including all
   *     previously defined properties
   */
  public TestInboundConnectorContext build() {
    return new TestInboundConnectorContext(secretProvider, validationProvider);
  }

  public class TestInboundConnectorContext extends AbstractConnectorContext
      implements InboundConnectorContext {

    private final List<Object> correlatedEvents = new ArrayList<>();

    private Health health = Health.unknown();

    protected TestInboundConnectorContext(
        SecretProvider secretProvider, ValidationProvider validationProvider) {
      super(secretProvider, validationProvider);

      replaceSecrets(properties);
    }

    @Override
    public InboundConnectorResult<?> correlate(Object variables) {
      correlatedEvents.add(variables);
      return result;
    }

    @Override
    public void cancel(Throwable exception) {
      // do nothing
    }

    @Override
    public Map<String, Object> getProperties() {
      return properties;
    }

    @Override
    public <T> T bindProperties(Class<T> cls) {
      var mappedObject = objectMapper.convertValue(properties, cls);
      if (validationProvider != null) {
        getValidationProvider().validate(mappedObject);
      }
      return mappedObject;
    }

    @Override
    public InboundConnectorDefinition getDefinition() {
      return definition;
    }

    @Override
    public ValidationProvider getValidationProvider() {
      return Optional.ofNullable(validationProvider).orElseGet(super::getValidationProvider);
    }

    @Override
    public void reportHealth(Health health) {
      this.health = health;
    }

    public List<Object> getCorrelations() {
      return correlatedEvents;
    }

    public Health getHealth() {
      return health;
    }
  }
}
