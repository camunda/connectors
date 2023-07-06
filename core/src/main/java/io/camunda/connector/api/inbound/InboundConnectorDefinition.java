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
package io.camunda.connector.api.inbound;

import io.camunda.connector.impl.inbound.ProcessCorrelationPoint;

public interface InboundConnectorDefinition {

  /** Returns the connector type. It is used by the runtime to find the connector implementation. */
  String type();

  /**
   * Returns the correlation point for this connector. Correlation point represents the point in
   * process diagram where the connector is invoked.
   */
  ProcessCorrelationPoint correlationPoint();

  /** Returns the BPMN process id of the process definition that contains the connector. */
  String bpmnProcessId();

  /** Returns the version of the process definition that contains the connector. */
  Integer version();

  /** Returns the process definition key of the process definition that contains the connector. */
  Long processDefinitionKey();

  /** Returns the element id of the connector in the process definition. */
  String elementId();

  /**
   * FEEL expression that is used to map connector output to process variables. Defined in connector
   * properties as {@link io.camunda.connector.impl.Constants#RESULT_EXPRESSION_KEYWORD}.
   */
  String resultExpression();

  /**
   * Name of the process variable that is used to store connector output. Defined in connector
   * properties as {@link io.camunda.connector.impl.Constants#RESULT_VARIABLE_KEYWORD}.
   */
  String resultVariable();

  /**
   * FEEL expression that evaluates to boolean and determines whether event should be triggered.
   * Defined in connector properties as {@link
   * io.camunda.connector.impl.Constants#ACTIVATION_CONDITION_KEYWORD}.
   */
  String activationCondition();
}
