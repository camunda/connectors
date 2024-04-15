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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.inbound.Activity;
import io.camunda.connector.api.inbound.CorrelationResult;
import io.camunda.connector.api.inbound.CorrelationResult.Success;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorDefinition;
import io.camunda.connector.api.inbound.InboundIntermediateConnectorContext;
import io.camunda.connector.api.inbound.ProcessElement;
import io.camunda.connector.api.inbound.ProcessElementContext;
import io.camunda.connector.api.inbound.ProcessInstanceContext;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.runtime.core.AbstractConnectorContext;
import io.camunda.connector.runtime.core.inbound.InboundConnectorElement;
import io.camunda.connector.runtime.core.inbound.InboundConnectorReportingContext;
import io.camunda.connector.test.ConnectorContextTestUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Test helper class for creating an {@link InboundConnectorContext} with a fluent API. */
public class InboundConnectorContextBuilder {

  protected final Map<String, String> secrets = new HashMap<>();
  protected SecretProvider secretProvider = secrets::get;
  protected Map<String, Object> properties;
  protected InboundConnectorDefinition definition;
  protected ValidationProvider validationProvider;

  protected ObjectMapper objectMapper = ConnectorsObjectMapperSupplier.DEFAULT_MAPPER;

  protected CorrelationResult result;

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
   * Provides the property value for the given name. Nested properties can be provided like
   * "foo.bar.baz".
   *
   * @param key - property name
   * @param value - property value
   * @return builder for fluent API
   */
  public InboundConnectorContextBuilder property(String key, String value) {
    if (properties == null) {
      properties = new HashMap<>();
    }
    ConnectorContextTestUtil.addVariable(key, value, properties);
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
    this.properties =
        (Map<String, Object>) ConnectorContextTestUtil.replaceImmutableMaps(properties);
    return this;
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
   * Provides multiple properties as object. The properties will then be converted to an
   * intermediate Map representation.
   *
   * @param propertiesAsJson - new properties
   * @return builder for fluent API
   */
  public InboundConnectorContextBuilder properties(String propertiesAsJson) {
    if (this.properties != null) {
      throw new IllegalStateException("Properties already set");
    }
    try {
      this.properties = objectMapper.readValue(propertiesAsJson, new TypeReference<>() {});
      return this;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
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

  public InboundConnectorContextBuilder result(CorrelationResult result) {
    this.result = result;
    return this;
  }

  /**
   * @return the {@link io.camunda.connector.api.inbound.InboundConnectorContext} including all
   *     previously defined properties
   */
  public TestInboundConnectorContext build() {
    return new TestInboundConnectorContext(secretProvider, validationProvider, result);
  }

  public class TestInboundConnectorContext extends AbstractConnectorContext
      implements InboundConnectorContext, InboundConnectorReportingContext {

    private final List<Object> correlatedEvents = new ArrayList<>();

    private Health health = Health.unknown();

    private final String propertiesWithSecrets;

    private final CorrelationResult result;

    protected TestInboundConnectorContext(
        SecretProvider secretProvider,
        ValidationProvider validationProvider,
        CorrelationResult result) {
      super(secretProvider, validationProvider);
      this.result = result;
      try {
        propertiesWithSecrets =
            getSecretHandler().replaceSecrets(objectMapper.writeValueAsString(properties));
      } catch (JsonProcessingException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void correlate(Object variables) {
      correlatedEvents.add(variables);
    }

    @Override
    public CorrelationResult correlateWithResult(Object variables) {
      correlate(variables);
      return Objects.requireNonNullElse(
          result,
          new Success.ProcessInstanceCreated(
              new ProcessElementContext() {

                @Override
                public ProcessElement getElement() {
                  return new ProcessElement("test", 0, 0, "test", "<default>");
                }

                @Override
                public <T> T bindProperties(Class<T> cls) {
                  return TestInboundConnectorContext.this.bindProperties(cls);
                }

                @Override
                public Map<String, Object> getProperties() {
                  return TestInboundConnectorContext.this.getProperties();
                }
              },
              0L,
              "test"));
    }

    @Override
    public void cancel(Throwable exception) {
      // do nothing
    }

    @Override
    public Map<String, Object> getProperties() {
      try {
        return objectMapper.readValue(propertiesWithSecrets, new TypeReference<>() {});
      } catch (JsonProcessingException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public <T> T bindProperties(Class<T> cls) {
      try {
        var mappedObject = objectMapper.readValue(propertiesWithSecrets, cls);
        if (validationProvider != null) {
          getValidationProvider().validate(mappedObject);
        }
        return mappedObject;
      } catch (JsonProcessingException e) {
        throw new RuntimeException(e);
      }
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

    @Override
    public Health getHealth() {
      return health;
    }

    @Override
    public void log(Activity activity) {}

    @Override
    public Queue<Activity> getLogs() {
      return new ConcurrentLinkedQueue<>();
    }

    @Override
    public List<InboundConnectorElement> connectorElements() {
      // never used in tests, runtime-specific method
      return null;
    }
  }

  /**
   * @return the {@link io.camunda.connector.api.inbound.InboundIntermediateConnectorContext}
   *     including all previously defined properties
   */
  public TestInboundIntermediateConnectorContext buildIntermediateConnectorContext() {
    return new TestInboundIntermediateConnectorContext(secretProvider, validationProvider);
  }

  public class TestInboundIntermediateConnectorContext extends TestInboundConnectorContext
      implements InboundIntermediateConnectorContext {

    protected TestInboundIntermediateConnectorContext(
        SecretProvider secretProvider, ValidationProvider validationProvider) {
      super(secretProvider, validationProvider, result);
    }

    @Override
    public List<ProcessInstanceContext> getProcessInstanceContexts() {
      return null;
    }
  }
}
