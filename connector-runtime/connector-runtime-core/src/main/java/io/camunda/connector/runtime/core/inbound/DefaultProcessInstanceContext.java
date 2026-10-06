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
import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.inbound.CorrelationRequest;
import io.camunda.connector.api.inbound.ProcessInstanceContext;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.feel.FeelEngineWrapperException;
import io.camunda.connector.feel.FeelExpressionEvaluator;
import io.camunda.connector.feel.FeelExpressionEvaluatorBuilder;
import io.camunda.connector.feel.jackson.FeelContextAwareObjectReader;
import io.camunda.connector.runtime.core.inbound.correlation.InboundCorrelationHandler;
import io.camunda.connector.runtime.core.secret.SecretReferenceResolver;
import io.camunda.connector.runtime.core.secret.SecretResolvingResultProcessor;
import io.camunda.connector.runtime.core.validation.ValidationUtil;
import java.io.IOException;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DefaultProcessInstanceContext implements ProcessInstanceContext {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultProcessInstanceContext.class);

  private final InboundIntermediateConnectorContextImpl context;
  private final ElementInstance elementInstance;
  private final ValidationProvider validationProvider;
  private final ObjectMapper objectMapper;
  private final InboundCorrelationHandler correlationHandler;
  private final FeelExpressionEvaluator evaluator;

  private final JsonNode processDefinitionProperties;

  public DefaultProcessInstanceContext(
      final InboundIntermediateConnectorContextImpl context,
      final ElementInstance elementInstance,
      final ValidationProvider validationProvider,
      final InboundCorrelationHandler correlationHandler,
      final ObjectMapper objectMapper,
      final CamundaClient camundaClient) {
    this.context = context;
    this.elementInstance = elementInstance;
    this.validationProvider =
        validationProvider == null
            ? ValidationUtil.discoverDefaultValidationProviderImplementation()
            : validationProvider;
    this.correlationHandler = correlationHandler;
    this.objectMapper = objectMapper;
    this.evaluator =
        FeelExpressionEvaluatorBuilder.camundaClient(camundaClient)
            .tenantId(context.getDefinition().tenantId())
            .scopeKey(elementInstance.getElementInstanceKey())
            .objectMapper(objectMapper)
            .resultProcessor(
                new SecretResolvingResultProcessor(new SecretReferenceResolver(camundaClient)))
            .build();
    this.processDefinitionProperties = objectMapper.valueToTree(context.getProperties());
  }

  @Override
  public Long getKey() {
    return elementInstance.getProcessInstanceKey();
  }

  @Override
  public Long getElementInstanceKey() {
    return elementInstance.getElementInstanceKey();
  }

  @Override
  public <T> T bind(final Class<T> cls) {
    try {
      T mappedObject =
          FeelContextAwareObjectReader.of(objectMapper)
              .withEvaluator(evaluator)
              .withAttribute(
                  DocumentFactory.PHYSICAL_TENANT_ID_ATTRIBUTE,
                  context.getDefinition().physicalTenantId())
              .readValue(processDefinitionProperties, cls);
      validationProvider.validate(mappedObject);
      return mappedObject;
    } catch (IOException | FeelEngineWrapperException e) {
      throw new RuntimeException(
          "Failed to bind process instance properties to "
              + cls.getName()
              + " using FEEL evaluation/deserialization"
              + " (tenantId="
              + context.getDefinition().tenantId()
              + ", scopeKey="
              + elementInstance.getElementInstanceKey()
              + ")",
          e);
    }
  }

  @Override
  public void correlate(final Object variables) {
    String messageId = elementInstance.getElementId() + elementInstance.getElementInstanceKey();
    // The executable can hold elements of several process versions, but the polled data belongs to
    // this instance only.
    var processDefinitionKey = elementInstance.getProcessDefinitionKey();
    var ownVersionElements =
        context.connectorElements().stream()
            .filter(e -> Objects.equals(processDefinitionKey, e.element().processDefinitionKey()))
            .toList();
    if (ownVersionElements.isEmpty()) {
      // the instance's version was deactivated since this context was created
      LOG.debug(
          "Not correlating element instance {}: its process version is no longer active",
          elementInstance.getElementInstanceKey());
      return;
    }
    correlationHandler.correlate(
        ownVersionElements,
        CorrelationRequest.builder().variables(variables).messageId(messageId).build());
  }

  @Override
  public String toString() {
    return "DefaultProcessInstanceContext{" + "flowNodeInstance=" + elementInstance + "}";
  }
}
