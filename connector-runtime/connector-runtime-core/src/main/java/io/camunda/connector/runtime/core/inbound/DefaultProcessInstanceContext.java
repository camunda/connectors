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
import io.camunda.connector.runtime.core.validation.ValidationUtil;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class DefaultProcessInstanceContext implements ProcessInstanceContext {

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
    // Not passing .objectMapper(objectMapper): connector-feel (and this cluster evaluator) is
    // Jackson 2-only (blocked on jackson-module-scala shipping a Jackson 3 release), while
    // objectMapper here is Jackson 3. The evaluator falls back to its own default Jackson 2
    // mapper, used only to merge input variables into a map for FEEL evaluation — not for final
    // result deserialization, which goes through FeelContextAwareObjectReader below instead.
    this.evaluator =
        FeelExpressionEvaluatorBuilder.camundaClient(camundaClient)
            .tenantId(context.getDefinition().tenantId())
            .scopeKey(elementInstance.getElementInstanceKey())
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
              .forType(cls)
              .readValue(processDefinitionProperties);
      validationProvider.validate(mappedObject);
      return mappedObject;
    } catch (JacksonException | FeelEngineWrapperException e) {
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
    correlationHandler.correlate(
        context.connectorElements(),
        CorrelationRequest.builder().variables(variables).messageId(messageId).build());
  }

  @Override
  public String toString() {
    return "DefaultProcessInstanceContext{" + "flowNodeInstance=" + elementInstance + "}";
  }
}
