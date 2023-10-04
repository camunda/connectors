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
import io.camunda.connector.api.inbound.InboundIntermediateConnectorContext;
import io.camunda.connector.api.inbound.ProcessInstanceContext;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.feel.FeelEngineWrapper;
import io.camunda.connector.feel.FeelEngineWrapperException;
import io.camunda.connector.runtime.core.inbound.correlation.InboundCorrelationHandler;
import io.camunda.connector.runtime.core.validation.ValidationUtil;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public final class DefaultProcessInstanceContext implements ProcessInstanceContext {

  private final InboundIntermediateConnectorContext context;
  private final FlowNodeInstance flowNodeInstance;
  private final ValidationProvider validationProvider;
  private final FeelEngineWrapper feelEngineWrapper;
  private final ObjectMapper objectMapper;
  private final Supplier<Map<String, Object>> operatePropertiesSupplier;
  private final InboundCorrelationHandler correlationHandler;

  public DefaultProcessInstanceContext(
      final InboundIntermediateConnectorContext context,
      final FlowNodeInstance flowNodeInstance,
      final ValidationProvider validationProvider,
      final FeelEngineWrapper feelEngineWrapper,
      final InboundCorrelationHandler correlationHandler,
      final ObjectMapper objectMapper,
      final Supplier<Map<String, Object>> operateVariables) {
    this.context = context;
    this.flowNodeInstance = flowNodeInstance;
    this.validationProvider =
        validationProvider == null
            ? ValidationUtil.discoverDefaultValidationProviderImplementation()
            : validationProvider;
    this.feelEngineWrapper = feelEngineWrapper;
    this.correlationHandler = correlationHandler;
    this.objectMapper = objectMapper;
    this.operatePropertiesSupplier = operateVariables;
  }

  @Override
  public Long getKey() {
    return flowNodeInstance.processInstanceKey();
  }

  @Override
  public <T> T bind(final Class<T> cls) {
    // TODO Replace with https://github.com/camunda/connectors/issues/1161
    HashMap<String, Object> copyOfProperties = new HashMap<>(context.getProperties());
    Map<String, Object> processVariables = operatePropertiesSupplier.get();
    evaluateAndPrepareForBinding(copyOfProperties, processVariables);
    copyOfProperties.putAll(processVariables);
    T mappedObject = objectMapper.convertValue(copyOfProperties, cls);
    validationProvider.validate(mappedObject);
    return mappedObject;
  }

  private void evaluateAndPrepareForBinding(
      final HashMap<String, Object> properties, final Map<String, Object> processVariables) {
    for (Map.Entry<String, Object> entry : properties.entrySet()) {
      String value = entry.getValue().toString();
      if (isFeelExpression(value)) {
        Object evaluatedValue = feelEngineWrapper.evaluate(value, processVariables);
        if (evaluatedValue == null) {
          throw new FeelEngineWrapperException("Evaluated value is null", value, properties);
        }
        entry.setValue(evaluatedValue);
      }
    }
  }

  private boolean isFeelExpression(String value) {
    return value != null && value.trim().startsWith("=");
  }

  @Override
  public void correlate(final Object variables) {
    String messageId = flowNodeInstance.flowNodeId() + flowNodeInstance.key();
    correlationHandler.correlate(
        (InboundConnectorDefinitionImpl) context.getDefinition(), variables, messageId);
  }

  @Override
  public String toString() {
    return "DefaultProcessInstanceContext{" + "flowNodeInstance=" + flowNodeInstance + "}";
  }
}
