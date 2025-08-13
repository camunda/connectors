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
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.document.DocumentReference;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.inbound.*;
import io.camunda.connector.api.inbound.CorrelationResult.Failure;
import io.camunda.connector.api.inbound.CorrelationResult.Failure.ActivationConditionNotMet;
import io.camunda.connector.api.inbound.CorrelationResult.Failure.InvalidInput;
import io.camunda.connector.api.inbound.CorrelationResult.Failure.Other;
import io.camunda.connector.api.inbound.CorrelationResult.Failure.ZeebeClientStatus;
import io.camunda.connector.api.inbound.CorrelationResult.Success;
import io.camunda.connector.api.inbound.CorrelationResult.Success.MessageAlreadyCorrelated;
import io.camunda.connector.api.inbound.CorrelationResult.Success.MessagePublished;
import io.camunda.connector.api.inbound.CorrelationResult.Success.ProcessInstanceCreated;
import io.camunda.connector.api.secret.SecretContext;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.feel.FeelEngineWrapperException;
import io.camunda.connector.runtime.core.AbstractConnectorContext;
import io.camunda.connector.runtime.core.inbound.activitylog.ActivityLogEntry;
import io.camunda.connector.runtime.core.inbound.activitylog.ActivityLogWriter;
import io.camunda.connector.runtime.core.inbound.activitylog.ActivitySource;
import io.camunda.connector.runtime.core.inbound.correlation.InboundCorrelationHandler;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails.ValidInboundConnectorDetails;
import io.camunda.document.DocumentFactoryImpl;
import io.camunda.document.store.InMemoryDocumentStore;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
  private final ActivityLogWriter activityLogWriter;
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
      ActivityLogWriter activityLogWriter) {
    super(secretProvider, validationProvider);
    this.documentFactory = documentFactory;
    this.correlationHandler = correlationHandler;
    this.connectorDetails = connectorDetails;
    this.properties =
        InboundPropertyHandler.readWrappedProperties(
            connectorDetails.rawPropertiesWithoutKeywords());
    this.objectMapper = objectMapper;
    this.cancellationCallback = cancellationCallback;
    this.activityLogWriter = activityLogWriter;
    this.activationTimestamp = System.currentTimeMillis();
  }

  public InboundConnectorContextImpl(
      SecretProvider secretProvider,
      ValidationProvider validationProvider,
      ValidInboundConnectorDetails connectorDetails,
      InboundCorrelationHandler correlationHandler,
      Consumer<Throwable> cancellationCallback,
      ObjectMapper objectMapper,
      ActivityLogWriter logs) {
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
      var result =
          correlationHandler.correlate(connectorDetails.connectorElements(), correlationRequest);
      logCorrelationResult(result);
      return result;
    } catch (ConnectorInputException connectorInputException) {
      return new CorrelationResult.Failure.InvalidInput(
          connectorInputException.getMessage(), connectorInputException);
    } catch (FeelEngineWrapperException feelEngineWrapperException) {
      log(
          activity ->
              activity
                  .withSeverity(Severity.ERROR)
                  .withMessage(
                      "Failed to evaluate FEEL expression: "
                          + feelEngineWrapperException.getMessage()));
      return new CorrelationResult.Failure.Other(feelEngineWrapperException);
    } catch (Exception exception) {
      log(
          activity ->
              activity
                  .withSeverity(Severity.ERROR)
                  .withMessage("Failed to correlate inbound event: " + exception.getMessage()));
      LOG.error("Failed to correlate inbound event", exception);
      return new CorrelationResult.Failure.Other(exception);
    }
  }

  private void logCorrelationResult(CorrelationResult correlationResult) {
    switch (correlationResult) {
      case Success success:
        logCorrelationSuccess(success);
        break;
      case Failure failure:
        logCorrelationFailure(failure);
        break;
    }
  }

  private void logCorrelationSuccess(Success success) {
    switch (success) {
      case ProcessInstanceCreated processInstanceCreated:
        logRuntime(
            activity ->
                activity
                    .withSeverity(Severity.INFO)
                    .withTag(ActivityLogTag.CORRELATION)
                    .withMessage("Process instance created")
                    .withData(
                        Map.of("processInstanceKey", processInstanceCreated.processInstanceKey())));
        break;
      case MessagePublished messagePublished:
        logRuntime(
            activity ->
                activity
                    .withSeverity(Severity.INFO)
                    .withTag(ActivityLogTag.CORRELATION)
                    .withMessage("Message published")
                    .withData(Map.of("messageKey", messagePublished.messageKey())));
        break;
      case MessageAlreadyCorrelated ignored:
        logRuntime(
            activity ->
                activity
                    .withSeverity(Severity.INFO)
                    .withTag(ActivityLogTag.CORRELATION)
                    .withMessage("Message already correlated"));
        break;
    }
  }

  private void logCorrelationFailure(Failure failure) {
    switch (failure) {
      case ActivationConditionNotMet ignored:
        logRuntime(
            activity ->
                activity
                    .withSeverity(Severity.WARNING)
                    .withTag(ActivityLogTag.CORRELATION)
                    .withMessage("Activation condition not met"));
        break;
      case InvalidInput ignored:
        logRuntime(
            activity ->
                activity
                    .withSeverity(Severity.ERROR)
                    .withTag(ActivityLogTag.CORRELATION)
                    .withMessage("Invalid input: " + failure.message()));
        break;
      case ZeebeClientStatus ignored:
        logRuntime(
            activity ->
                activity
                    .withSeverity(Severity.ERROR)
                    .withTag(ActivityLogTag.CORRELATION)
                    .withMessage("Zeebe client status error: " + failure.message()));
        break;
      case Other ignored:
        logRuntime(
            activity ->
                activity
                    .withSeverity(Severity.ERROR)
                    .withTag(ActivityLogTag.CORRELATION)
                    .withMessage("Other error: " + failure.message()));
        break;
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
    var activityLog = Activity.newBuilder().andReportHealth(health).build();
    // append the activity log to store the health status change history
    activityLogWriter.log(
        new ActivityLogEntry(
            ExecutableId.fromDeduplicationId(connectorDetails.deduplicationId()),
            ActivitySource.CONNECTOR,
            activityLog));
  }

  @Override
  public Health getHealth() {
    return health;
  }

  @Override
  public void log(Activity log) {
    if (log.healthChange() != null) {
      this.health = log.healthChange();
    }
    activityLogWriter.log(
        new ActivityLogEntry(
            ExecutableId.fromDeduplicationId(connectorDetails.deduplicationId()),
            ActivitySource.CONNECTOR,
            log));
  }

  @Override
  public void log(Consumer<ActivityBuilder> activityBuilderConsumer) {
    if (activityBuilderConsumer == null) {
      throw new IllegalArgumentException("Activity builder consumer cannot be null");
    }
    var builder = Activity.newBuilder();
    activityBuilderConsumer.accept(builder);
    log(builder.build());
  }

  private void logRuntime(Consumer<ActivityBuilder> activityBuilderConsumer) {
    var builder = Activity.newBuilder();
    activityBuilderConsumer.accept(builder);
    activityLogWriter.log(
        new ActivityLogEntry(
            ExecutableId.fromDeduplicationId(connectorDetails.deduplicationId()),
            ActivitySource.RUNTIME,
            builder.build()));
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
              getSecretHandler(),
              objectMapper,
              properties,
              new SecretContext(connectorDetails.tenantId()));
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
