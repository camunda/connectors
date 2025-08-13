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
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentReference;
import io.camunda.connector.api.inbound.*;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.runtime.core.inbound.correlation.InboundCorrelationHandler;
import io.camunda.connector.runtime.core.inbound.correlation.MessageCorrelationPoint.BoundaryEventCorrelationPoint;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Implementation of {@link InboundIntermediateConnectorContext} that extends {@link
 * InboundConnectorContext} and enables runtime updates of context properties from Camunda.
 */
public class InboundIntermediateConnectorContextImpl
    implements InboundIntermediateConnectorContext, InboundConnectorReportingContext {
  private final InboundConnectorReportingContext inboundContext;
  private final ProcessInstanceClient processInstanceClient;
  private final ValidationProvider validationProvider;
  private final ObjectMapper objectMapper;
  private final InboundCorrelationHandler correlationHandler;
  private final Long activationTimestamp;

  public InboundIntermediateConnectorContextImpl(
      final InboundConnectorReportingContext inboundContext,
      final ProcessInstanceClient processInstanceClient,
      final ValidationProvider validationProvider,
      final ObjectMapper objectMapper,
      final InboundCorrelationHandler correlationHandler) {
    this.inboundContext = inboundContext;
    this.processInstanceClient = processInstanceClient;
    this.validationProvider = validationProvider;
    this.objectMapper = objectMapper;
    this.correlationHandler = correlationHandler;
    this.activationTimestamp = System.currentTimeMillis();
  }

  @Override
  public List<ProcessInstanceContext> getProcessInstanceContexts() {
    var elements = connectorElements();

    return elements.stream()
        .map(
            elementInfo -> {
              var elementId = elementInfo.element().elementId();
              if (elementInfo.correlationPoint() instanceof BoundaryEventCorrelationPoint point) {
                elementId = point.attachedTo().elementId();
              }
              return processInstanceClient.fetchActiveProcessInstanceKeyByDefinitionKeyAndElementId(
                  elementInfo.element().processDefinitionKey(), elementId);
            })
        .flatMap(List::stream)
        .map(this::createProcessInstanceContext)
        .collect(Collectors.toList());
  }

  private ProcessInstanceContext createProcessInstanceContext(ElementInstance elementInstance) {
    Supplier<Map<String, Object>> variableSupplier =
        () ->
            processInstanceClient.fetchVariablesByProcessInstanceKey(
                elementInstance.getProcessInstanceKey());

    return new DefaultProcessInstanceContext(
        this,
        elementInstance,
        validationProvider,
        correlationHandler,
        objectMapper,
        variableSupplier);
  }

  @Override
  public CorrelationResult correlateWithResult(Object variables) {
    return inboundContext.correlate(CorrelationRequest.builder().variables(variables).build());
  }

  @Override
  public CorrelationResult correlate(CorrelationRequest correlationRequest) {
    return inboundContext.correlate(correlationRequest);
  }

  @Override
  public ActivationCheckResult canActivate(Object variables) {
    return inboundContext.canActivate(variables);
  }

  @Override
  public void cancel(final Throwable exception) {
    inboundContext.cancel(exception);
  }

  @Override
  public Map<String, Object> getProperties() {
    return inboundContext.getProperties();
  }

  @Override
  public <T> T bindProperties(final Class<T> cls) {
    return inboundContext.bindProperties(cls);
  }

  @Override
  public InboundConnectorDefinition getDefinition() {
    return inboundContext.getDefinition();
  }

  @Override
  public void reportHealth(final Health health) {
    inboundContext.reportHealth(health);
  }

  @Override
  public Health getHealth() {
    return inboundContext.getHealth();
  }

  @Override
  public void log(Activity log) {
    inboundContext.log(log);
  }

  @Override
  public void log(Consumer<ActivityBuilder> activityBuilderConsumer) {
    inboundContext.log(activityBuilderConsumer);
  }

  @Override
  public List<InboundConnectorElement> connectorElements() {
    return inboundContext.connectorElements();
  }

  @Override
  public Long getActivationTimestamp() {
    return activationTimestamp;
  }

  @Override
  public Document resolve(DocumentReference reference) {
    return inboundContext.resolve(reference);
  }

  @Override
  public Document create(DocumentCreationRequest request) {
    return inboundContext.create(request);
  }
}
