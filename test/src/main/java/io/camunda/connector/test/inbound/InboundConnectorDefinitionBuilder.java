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
package io.camunda.connector.test.inbound;

import io.camunda.connector.api.inbound.InboundConnectorDefinition;
import io.camunda.connector.api.inbound.correlation.ProcessCorrelationPoint;
import io.camunda.connector.api.inbound.correlation.StartEventCorrelationPoint;

/** Test helper class for creating an {@link InboundConnectorDefinition} with a fluent API. */
public class InboundConnectorDefinitionBuilder {

  private String type = "test-connector";
  private String bpmnProcessId = "test-process";
  private int version = 1;
  private long processDefinitionKey = 1;
  private String elementId = "test-element";
  private ProcessCorrelationPoint correlationPoint =
      new StartEventCorrelationPoint(bpmnProcessId, version, processDefinitionKey);

  public static InboundConnectorDefinitionBuilder create() {
    return new InboundConnectorDefinitionBuilder();
  }

  public InboundConnectorDefinitionBuilder version(int version) {
    this.version = version;
    return this;
  }

  public InboundConnectorDefinitionBuilder processDefinitionKey(long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public InboundConnectorDefinitionBuilder elementId(String elementId) {
    this.elementId = elementId;
    return this;
  }

  public InboundConnectorDefinitionBuilder correlationPoint(
      ProcessCorrelationPoint correlationPoint) {
    this.correlationPoint = correlationPoint;
    return this;
  }

  public InboundConnectorDefinitionBuilder type(String type) {
    this.type = type;
    return this;
  }

  public InboundConnectorDefinitionBuilder bpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public InboundConnectorDefinition build() {
    return new InboundConnectorDefinitionImpl(
        type, correlationPoint, bpmnProcessId, version, processDefinitionKey, elementId);
  }

  public record InboundConnectorDefinitionImpl(
      String type,
      ProcessCorrelationPoint correlationPoint,
      String bpmnProcessId,
      Integer version,
      Long processDefinitionKey,
      String elementId)
      implements InboundConnectorDefinition {}
}
