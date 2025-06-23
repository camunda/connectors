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
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.inbound.*;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.feel.FeelEngineWrapperException;
import io.camunda.connector.runtime.core.AbstractConnectorContext;
import io.camunda.connector.runtime.core.inbound.correlation.InboundCorrelationHandler;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails.ValidInboundConnectorDetails;
import io.camunda.document.Document;
import io.camunda.document.factory.DocumentFactory;
import io.camunda.document.factory.DocumentFactoryImpl;
import io.camunda.document.reference.DocumentReference;
import io.camunda.document.store.DocumentCreationRequest;
import io.camunda.document.store.InMemoryDocumentStore;
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
  private final EvictingQueue<Activity> logs;
  private final DocumentFactory documentFactory;
  private final Long activationTimestamp;
  private Health health = Health.unknown();
  private Map<String, Object> propertiesWithSecrets;

  public InboundConnectorContextImpl(
      SecretProvider secretProvider,
      ValidationProvider validationProvider,
      DocumentFactory documentFactory,
      ValidInboundConnectorDetails connectorDetails,
      InboundCorrelationHandler correlationHandler,
      Consumer<Throwable> cancellationCallback,
      ObjectMapper objectMapper,
      EvictingQueue<Activity> logs) {
    super(secretProvider, validationProvider);
    this.documentFactory = documentFactory;
    this.correlationHandler = correlationHandler;
    this.connectorDetails = connectorDetails;
    this.properties =
        InboundPropertyHandler.readWrappedProperties(
            connectorDetails.rawPropertiesWithoutKeywords());
    this.objectMapper = objectMapper;
    this.cancellationCallback = cancellationCallback;
    this.logs = logs;
    this.activationTimestamp = System.currentTimeMillis();
  }

  public InboundConnectorContextImpl(
      SecretProvider secretProvider,
      ValidationProvider validationProvider,
      ValidInboundConnectorDetails connectorDetails,
      InboundCorrelationHandler correlationHandler,
      Consumer<Throwable> cancellationCallback,
      ObjectMapper objectMapper,
      EvictingQueue<Activity> logs) {
    this(
        secretProvider,
        validationProvider,
        new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE),
        connectorDetails,
        correlationHandler,
        cancellationCallback,
        objectMapper,
        logs);
  }

  @Override
  public ActivationCheckResult canActivate(Object variables) {
    return correlationHandler.canActivate(connectorDetails.connectorElements(), variables);
  }

  @Override
  public CorrelationResult correlateWithResult(Object variables) {
    return this.correlateWithResultInternal(
        CorrelationRequest.builder().variables(variables).build());
  }

  @Override
  public CorrelationResult correlate(CorrelationRequest correlationRequest) {
    return this.correlateWithResultInternal(correlationRequest);
  }

  private CorrelationResult correlateWithResultInternal(CorrelationRequest correlationRequest) {
    try {
      return correlationHandler.correlate(connectorDetails.connectorElements(), correlationRequest);
    } catch (ConnectorInputException connectorInputException) {
      return new CorrelationResult.Failure.InvalidInput(
          connectorInputException.getMessage(), connectorInputException);
    } catch (FeelEngineWrapperException feelEngineWrapperException) {
      log(
          Activity.level(Severity.ERROR)
              .tag("error")
              .message(feelEngineWrapperException.getMessage()));
      return new CorrelationResult.Failure.Other(feelEngineWrapperException);
    } catch (Exception exception) {
      log(
          Activity.level(Severity.ERROR)
              .tag("error")
              .message("Failed to correlate inbound event " + exception.getMessage()));
      LOG.error("Failed to correlate inbound event", exception);
      return new CorrelationResult.Failure.Other(exception);
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
    switch (log.severity()) {
      case DEBUG -> LOG.debug(log.toString());
      case ERROR -> LOG.error(log.toString());
      case INFO -> LOG.info(log.toString());
      case WARNING -> LOG.warn(log.toString());
    }
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

  @Override
  public Long getActivationTimestamp() {
    return activationTimestamp;
  }

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

  @Override
  public Document resolve(DocumentReference reference) {
    return documentFactory.resolve(reference);
  }

  @Override
  public Document create(DocumentCreationRequest request) {
    return documentFactory.create(request);
  }
}
