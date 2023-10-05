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

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.connector.api.inbound.InboundConnectorDefinition;
import io.camunda.connector.runtime.core.Keywords;
import io.camunda.connector.runtime.core.inbound.correlation.ProcessCorrelationPoint;
import java.util.Map;
import java.util.Optional;

/** Inbound connector definition implementation that also contains connector properties */
public record InboundConnectorDefinitionImpl(
    @JsonIgnore Map<String, String> rawProperties,
    ProcessCorrelationPoint correlationPoint,
    String bpmnProcessId,
    Integer version,
    Long processDefinitionKey,
    String elementId,
    String tenantId)
    implements InboundConnectorDefinition {

  @Override
  public String type() {
    return Optional.ofNullable(rawProperties.get(Keywords.INBOUND_TYPE_KEYWORD))
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Missing connector type property. The connector element template is not valid"));
  }

  public String resultExpression() {
    return rawProperties.get(Keywords.RESULT_EXPRESSION_KEYWORD);
  }

  public String resultVariable() {
    return rawProperties.get(Keywords.RESULT_VARIABLE_KEYWORD);
  }

  public String activationCondition() {
    return Optional.ofNullable(rawProperties.get(Keywords.ACTIVATION_CONDITION_KEYWORD))
        .orElseGet(() -> rawProperties.get(Keywords.DEPRECATED_ACTIVATION_CONDITION_KEYWORD));
  }

  // override to exclude rawProperties
  @Override
  public String toString() {
    return "InboundConnectorDefinitionImpl{"
        + "correlationPoint="
        + correlationPoint
        + ", bpmnProcessId='"
        + bpmnProcessId
        + '\''
        + ", version="
        + version
        + ", processDefinitionKey="
        + processDefinitionKey
        + ", elementId='"
        + elementId
        + ", tenantId='"
        + tenantId
        + '\''
        + '}';
  }
}
