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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.inbound.ProcessInstanceContext;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.feel.jackson.FeelContextAwareObjectReader;
import io.camunda.connector.runtime.core.inbound.correlation.InboundCorrelationHandler;
import io.camunda.connector.runtime.core.validation.ValidationUtil;
import io.camunda.zeebe.client.api.search.response.FlowNodeInstance;
import java.util.Map;
import java.util.function.Supplier;

public final class DefaultProcessInstanceContext implements ProcessInstanceContext {

  private final InboundIntermediateConnectorContextImpl context;
  private final FlowNodeInstance flowNodeInstance;
  private final ValidationProvider validationProvider;
  private final ObjectMapper objectMapper;
  private final Supplier<Map<String, Object>> operatePropertiesSupplier;
  private final InboundCorrelationHandler correlationHandler;

  private final JsonNode processDefinitionProperties;

  public DefaultProcessInstanceContext(
      final InboundIntermediateConnectorContextImpl context,
      final FlowNodeInstance flowNodeInstance,
      final ValidationProvider validationProvider,
      final InboundCorrelationHandler correlationHandler,
      final ObjectMapper objectMapper,
      final Supplier<Map<String, Object>> operateVariables) {
    this.context = context;
    this.flowNodeInstance = flowNodeInstance;
    this.validationProvider =
        validationProvider == null
            ? ValidationUtil.discoverDefaultValidationProviderImplementation()
            : validationProvider;
    this.correlationHandler = correlationHandler;
    this.objectMapper = objectMapper;
    this.operatePropertiesSupplier = operateVariables;

    processDefinitionProperties = objectMapper.valueToTree(context.getProperties());
  }

  @Override
  public Long getKey() {
    return flowNodeInstance.getProcessInstanceKey();
  }

  @Override
  public <T> T bind(final Class<T> cls) {
    try {
      T mappedObject =
          FeelContextAwareObjectReader.of(objectMapper)
              .withContextSupplier(operatePropertiesSupplier)
              .readValue(processDefinitionProperties, cls);
      validationProvider.validate(mappedObject);
      return mappedObject;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void correlate(final Object variables) {
    String messageId = flowNodeInstance.getFlowNodeId() + flowNodeInstance.getFlowNodeInstanceKey();
    correlationHandler.correlate(context.connectorElements(), variables, messageId);
  }

  @Override
  public String toString() {
    return "DefaultProcessInstanceContext{" + "flowNodeInstance=" + flowNodeInstance + "}";
  }
}
