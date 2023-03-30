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

import static io.camunda.connector.impl.Constants.ACTIVATION_CONDITION_KEYWORD;
import static io.camunda.connector.impl.Constants.CORRELATION_KEY_EXPRESSION_KEYWORD;
import static io.camunda.connector.impl.Constants.INBOUND_TYPE_KEYWORD;
import static io.camunda.connector.impl.Constants.RESULT_EXPRESSION_KEYWORD;
import static io.camunda.connector.impl.Constants.RESULT_VARIABLE_KEYWORD;

import io.camunda.connector.impl.inbound.InboundConnectorProperties;
import io.camunda.connector.impl.inbound.ProcessCorrelationPoint;
import io.camunda.connector.impl.inbound.correlation.StartEventCorrelationPoint;
import java.util.HashMap;
import java.util.Map;

/** Test helper class for creating an {@link InboundConnectorProperties} with a fluent API. */
public class InboundConnectorPropertiesBuilder {

  private final Map<String, String> properties = new HashMap<>();
  private String bpmnProcessId = "test-process";
  private int version = 1;
  private long processDefinitionKey = 1;
  private ProcessCorrelationPoint correlationPoint =
      new StartEventCorrelationPoint(processDefinitionKey, bpmnProcessId, version);

  public static InboundConnectorPropertiesBuilder create() {
    return new InboundConnectorPropertiesBuilder();
  }

  public InboundConnectorPropertiesBuilder type(String type) {
    if (properties.containsKey(INBOUND_TYPE_KEYWORD)) {
      throw new IllegalArgumentException("Type already set");
    }
    properties.put(INBOUND_TYPE_KEYWORD, type);
    return this;
  }

  public InboundConnectorPropertiesBuilder bpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public InboundConnectorPropertiesBuilder version(int version) {
    this.version = version;
    return this;
  }

  public InboundConnectorPropertiesBuilder processDefinitionKey(long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public InboundConnectorPropertiesBuilder correlationPoint(
      ProcessCorrelationPoint correlationPoint) {
    this.correlationPoint = correlationPoint;
    return this;
  }

  public InboundConnectorPropertiesBuilder correlationKeyExpression(String expression) {
    if (properties.containsKey(CORRELATION_KEY_EXPRESSION_KEYWORD)) {
      throw new IllegalArgumentException("Correlation key expression already set");
    }
    properties.put(CORRELATION_KEY_EXPRESSION_KEYWORD, expression);
    return this;
  }

  public InboundConnectorPropertiesBuilder activationCondition(String condition) {
    if (properties.containsKey(ACTIVATION_CONDITION_KEYWORD)) {
      throw new IllegalArgumentException("Activation condition already set");
    }
    properties.put(ACTIVATION_CONDITION_KEYWORD, condition);
    return this;
  }

  public InboundConnectorPropertiesBuilder resultVariable(String variable) {
    if (properties.containsKey(RESULT_VARIABLE_KEYWORD)) {
      throw new IllegalArgumentException("Result variable already set");
    }
    properties.put(RESULT_VARIABLE_KEYWORD, variable);
    return this;
  }

  public InboundConnectorPropertiesBuilder resultExpression(String expression) {
    if (properties.containsKey(RESULT_EXPRESSION_KEYWORD)) {
      throw new IllegalArgumentException("Result variable already set");
    }
    properties.put(RESULT_EXPRESSION_KEYWORD, expression);
    return this;
  }

  public InboundConnectorPropertiesBuilder property(String key, String value) {
    if (properties.containsKey(key)) {
      throw new IllegalArgumentException("Property already set");
    }
    properties.put(key, value);
    return this;
  }

  public InboundConnectorProperties build() {
    if (!properties.containsKey(INBOUND_TYPE_KEYWORD)) {
      properties.put(INBOUND_TYPE_KEYWORD, "io.camunda:test-inbound-connector:1");
    }
    return new InboundConnectorProperties(
        correlationPoint, properties, bpmnProcessId, version, processDefinitionKey);
  }
}
