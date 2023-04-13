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
package io.camunda.connector.impl;

public class Constants {

  /**
   * The keyword that identifies the source of `result variable` property of a Connector. Result
   * variable is a property that contains the name of the process variable where the Connector
   * result will be stored.
   *
   * <p>For outbound Connectors, this value comes from Zeebe job headers.
   *
   * <p>For inbound Connectors, this value comes from the extension properties of a BPMN element.
   */
  public static final String RESULT_VARIABLE_KEYWORD = "resultVariable";

  /**
   * The keyword that identifies the source of `result expression` property of a Connector. Result
   * expression is a FEEL expression that is used to map the Connector output into process variables
   *
   * <p>For outbound Connectors, this value comes from Zeebe job headers.
   *
   * <p>For inbound Connectors, this value comes from the extension properties of a BPMN element.
   */
  public static final String RESULT_EXPRESSION_KEYWORD = "resultExpression";

  /**
   * The keyword that identifies the source of `error expression` property of a Connector. Error
   * expression is a FEEL context expression that is used to map the Connector output into process
   * variables
   *
   * <p>This value only exists for outbound Connectors and comes from Zeebe job headers.
   */
  public static final String ERROR_EXPRESSION_KEYWORD = "errorExpression";

  /**
   * The keyword that identifies the source of `correlation key expression` property of a Connector.
   * Correlation key expression is a FEEL expression that is extracts the correlation key from the
   * inbound Connector output.
   *
   * <p>This value only exists for inbound Connectors that target an intermediate message catch
   * event and comes from the extension properties of a BPMN element.
   */
  public static final String CORRELATION_KEY_EXPRESSION_KEYWORD = "correlationKeyExpression";

  /**
   * The keyword that identifies the source of `activation condition` property of a Connector.
   * Activation condition is a boolean FEEL expression that determines whether the inbound Connector
   * should be activated based on the inbound payload.
   *
   * <p>This value only exists for inbound Connectors and comes from the extension properties of a
   * BPMN element.
   */
  public static final String ACTIVATION_CONDITION_KEYWORD = "activationCondition";

  /**
   * The keyword that identifies the source of `type` property of an inbound Connector. Type
   * identifies the specific inbound Connector implementation.
   */
  public static final String INBOUND_TYPE_KEYWORD = "inbound.type";

  public static final String LEGACY_VARIABLE_MAPPING_KEYWORD = "inbound.variableMapping";
}
