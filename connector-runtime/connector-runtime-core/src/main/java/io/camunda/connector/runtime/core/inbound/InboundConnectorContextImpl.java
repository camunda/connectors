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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.inbound.CorrelationResult;
import io.camunda.connector.api.inbound.CorrelationResult.Failure.ActivationConditionNotMet;
import io.camunda.connector.api.inbound.CorrelationResult.Failure.Other;
import io.camunda.connector.api.inbound.CorrelationResult.Success;
import com.google.common.collect.EvictingQueue;
import io.camunda.connector.api.inbound.Activity;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorDefinition;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.runtime.core.AbstractConnectorContext;
import io.camunda.connector.runtime.core.inbound.correlation.InboundCorrelationHandler;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InboundConnectorContextImpl extends AbstractConnectorContext
    implements InboundConnectorContext, InboundConnectorReportingContext {

  private final Logger LOG = LoggerFactory.getLogger(InboundConnectorContextImpl.class);
  private final InboundConnectorDefinitionImpl definition;
  private final Map<String, Object> properties;

  private final InboundCorrelationHandler correlationHandler;
  private final ObjectMapper objectMapper;

  private final Consumer<Throwable> cancellationCallback;

  private Health health = Health.unknown();

  private EvictingQueue<Activity> logs;

  public InboundConnectorContextImpl(
      SecretProvider secretProvider,
      ValidationProvider validationProvider,
      InboundConnectorDefinitionImpl definition,
      InboundCorrelationHandler correlationHandler,
      Consumer<Throwable> cancellationCallback,
      ObjectMapper objectMapper,
      EvictingQueue logs) {
    super(secretProvider, validationProvider);
    this.correlationHandler = correlationHandler;
    this.definition = definition;
    this.properties = InboundPropertyHandler.readWrappedProperties(definition.rawProperties());
    this.objectMapper = objectMapper;
    this.cancellationCallback = cancellationCallback;
    this.logs = logs;
  }

  @Override
  public void correlate(Object variables) {
    var result = correlationHandler.correlate(definition, variables);
    if (result == null) {
      throw new ConnectorException("Failed to correlate inbound event, result is null");
    }
    if (result instanceof ActivationConditionNotMet || result instanceof Success) {
      return;
    }
    if (result instanceof CorrelationResult.Failure.InvalidInput invalidInput) {
      throw new ConnectorInputException(invalidInput.message(), invalidInput.error());
    }
    if (result instanceof Other exception) {
      throw new ConnectorException(exception.error());
    }
    throw new ConnectorException("Failed to correlate inbound event, details: " + result);
  }

  @Override
  public CorrelationResult correlateWithResult(Object variables) {
    try {
      return correlationHandler.correlate(definition, variables);
    } catch (Exception e) {
      LOG.error("Failed to correlate inbound event", e);
      return new CorrelationResult.Failure.Other(e);
    }
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
    return getPropertiesWithSecrets();
  }

  @Override
  public <T> T bindProperties(Class<T> cls) {
    var mappedObject = objectMapper.convertValue(getPropertiesWithSecrets(), cls);
    getValidationProvider().validate(mappedObject);
    return mappedObject;
  }

  @Override
  public InboundConnectorDefinition getDefinition() {
    return definition;
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
  public void log(Activity log) {
    this.logs.add(log);
  }

  @Override
  public Queue<Activity> getLogs() {
    return this.logs;
  }

  private Map<String, Object> propertiesWithSecrets;

  private Map<String, Object> getPropertiesWithSecrets() {
    if (propertiesWithSecrets == null) {
      try {
        var propertiesAsJsonString = objectMapper.writeValueAsString(properties);
        var propertiesWithSecretsJson = getSecretHandler().replaceSecrets(propertiesAsJsonString);
        propertiesWithSecrets =
            objectMapper.readValue(propertiesWithSecretsJson, new TypeReference<>() {});
      } catch (JsonProcessingException e) {
        throw new RuntimeException(e);
      }
    }
    return propertiesWithSecrets;
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
  public int hashCode() {
    return Objects.hash(definition);
  }

  @Override
  public String toString() {
    return "InboundConnectorContextImpl{" + "definition=" + definition + '}';
  }
}
