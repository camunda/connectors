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
import com.google.common.collect.EvictingQueue;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.inbound.Activity;
import io.camunda.connector.api.inbound.CorrelationResult;
import io.camunda.connector.api.inbound.CorrelationResult.Failure.ActivationConditionNotMet;
import io.camunda.connector.api.inbound.CorrelationResult.Failure.Other;
import io.camunda.connector.api.inbound.CorrelationResult.Success;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorDefinition;
import io.camunda.connector.api.inbound.Severity;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.feel.FeelEngineWrapperException;
import io.camunda.connector.runtime.core.AbstractConnectorContext;
import io.camunda.connector.runtime.core.inbound.correlation.InboundCorrelationHandler;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InboundConnectorContextImpl extends AbstractConnectorContext
    implements InboundConnectorContext, InboundConnectorReportingContext {

  private final Logger LOG = LoggerFactory.getLogger(InboundConnectorContextImpl.class);
  private final InboundConnectorDetails connectorDetails;
  private final Map<String, Object> properties;

  private final InboundCorrelationHandler correlationHandler;
  private final ObjectMapper objectMapper;

  private final Consumer<Throwable> cancellationCallback;

  private Health health = Health.unknown();

  private final EvictingQueue<Activity> logs;

  public InboundConnectorContextImpl(
      SecretProvider secretProvider,
      ValidationProvider validationProvider,
      InboundConnectorDetails connectorDetails,
      InboundCorrelationHandler correlationHandler,
      Consumer<Throwable> cancellationCallback,
      ObjectMapper objectMapper,
      EvictingQueue logs) {
    super(secretProvider, validationProvider);
    this.correlationHandler = correlationHandler;
    this.connectorDetails = connectorDetails;
    this.properties =
        InboundPropertyHandler.readWrappedProperties(
            connectorDetails.rawPropertiesWithoutKeywords());
    this.objectMapper = objectMapper;
    this.cancellationCallback = cancellationCallback;
    this.logs = logs;
  }

  @Override
  public void correlate(Object variables) {
    var result = correlateWithResult(variables);
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
      return correlationHandler.correlate(connectorDetails.connectorElements(), variables);
    } catch (FeelEngineWrapperException e) {
      log(Activity.level(Severity.ERROR).tag("error").message(e.getMessage()));
      return new CorrelationResult.Failure.Other(e);
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
    return getPropertiesWithSecrets(properties);
  }

  @Override
  public <T> T bindProperties(Class<T> cls) {
    var mappedObject = objectMapper.convertValue(getPropertiesWithSecrets(properties), cls);
    getValidationProvider().validate(mappedObject);
    return mappedObject;
  }

  @Override
  public InboundConnectorDefinition getDefinition() {
    return new InboundConnectorDefinition(
        connectorDetails.type(),
        connectorDetails.tenantId(),
        connectorDetails.deduplicationId(),
        connectorDetails.connectorElements().stream()
            .map(InboundConnectorElement::element)
            .collect(Collectors.toList()));
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

  @Override
  public List<InboundConnectorElement> connectorElements() {
    return connectorDetails.connectorElements();
  }

  private Map<String, Object> propertiesWithSecrets;

  private Map<String, Object> getPropertiesWithSecrets(Map<String, Object> properties) {
    if (propertiesWithSecrets == null) {
      propertiesWithSecrets =
          InboundPropertyHandler.getPropertiesWithSecrets(
              getSecretHandler(), objectMapper, properties);
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
    return Objects.equals(connectorDetails, that.connectorDetails);
  }

  @Override
  public int hashCode() {
    return Objects.hash(connectorDetails);
  }

  @Override
  public String toString() {
    return "InboundConnectorContextImpl{" + "connectorDetails=" + connectorDetails + '}';
  }
}
