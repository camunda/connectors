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
package io.camunda.connector.runtime.core.inbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorDefinition;
import io.camunda.connector.api.inbound.InboundConnectorResult;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.impl.Constants;
import io.camunda.connector.impl.context.AbstractConnectorContext;
import io.camunda.connector.runtime.core.feel.FeelParserWrapper;
import io.camunda.connector.runtime.core.inbound.correlation.InboundCorrelationHandler;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InboundConnectorContextImpl extends AbstractConnectorContext
    implements InboundConnectorContext {

  private final Logger LOG = LoggerFactory.getLogger(InboundConnectorContextImpl.class);
  private final InboundConnectorDefinitionImpl definition;
  private final Map<String, Object> properties;

  private final InboundCorrelationHandler correlationHandler;
  private final ObjectMapper objectMapper;

  private final Consumer<Throwable> cancellationCallback;

  private Health health = Health.unknown();

  public InboundConnectorContextImpl(
      SecretProvider secretProvider,
      ValidationProvider validationProvider,
      InboundConnectorDefinitionImpl definition,
      InboundCorrelationHandler correlationHandler,
      Consumer<Throwable> cancellationCallback,
      ObjectMapper objectMapper) {
    super(secretProvider, validationProvider);
    this.correlationHandler = correlationHandler;
    this.definition = definition;
    this.properties = InboundPropertyHandler.readWrappedProperties(definition.rawProperties());
    this.objectMapper = objectMapper;
    this.cancellationCallback = cancellationCallback;
  }

  @Override
  public InboundConnectorResult<?> correlate(Object variables) {
    return correlationHandler.correlate(definition, variables);
  }

  @Override
  public void cancel(Throwable exception) {
    try {
      cancellationCallback.accept(exception);
    } catch (Throwable e) {
      LOG.error("Failed to deliver the cancellation signal to the runtime", e);
    }
  }

  @Override
  public Map<String, Object> getProperties() {
    return getPropertiesWithSecrets(properties);
  }

  @Override
  public <T> T bindProperties(Class<T> cls) {
    var evaluatedProps = getPropertiesWithFeel(properties);
    var propsWithSecrets = getPropertiesWithSecrets(evaluatedProps);
    var mappedObject = objectMapper.convertValue(propsWithSecrets, cls);
    getValidationProvider().validate(mappedObject);
    return mappedObject;
  }

  @Override
  public InboundConnectorDefinition getDefinition() {
    return definition;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    InboundConnectorContextImpl that = (InboundConnectorContextImpl) o;
    return Objects.equals(definition, that.definition);
  }

  @Override
  public void reportHealth(Health health) {
    this.health = health;
  }

  @Override
  public Health getHealth() {
    return health;
  }

  @Override
  public int hashCode() {
    return Objects.hash(definition);
  }

  @Override
  public String toString() {
    return "InboundConnectorContextImpl{" + "definition=" + definition + '}';
  }

  private Map<String, Object> propertiesWithFeel;

  private Map<String, Object> getPropertiesWithFeel(Map<String, Object> properties) {
    if (propertiesWithFeel == null) {
      propertiesWithFeel =
          properties.entrySet().stream()
              .map(
                  entry -> {
                    if (Constants.RESERVED_KEYWORDS.contains(entry.getKey())) {
                      return entry;
                    } else {
                      return Map.entry(
                          entry.getKey(),
                          FeelParserWrapper.parseIfIsFeelExpressionOrGetOriginal(entry.getValue()));
                    }
                  })
              .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
    return propertiesWithFeel;
  }

  private Map<String, Object> propertiesWithSecrets;

  private Map<String, Object> getPropertiesWithSecrets(Map<String, Object> properties) {
    if (propertiesWithSecrets == null) {
      propertiesWithSecrets = new HashMap<>(properties);
      replaceSecrets(propertiesWithSecrets);
    }
    return propertiesWithSecrets;
  }
}
