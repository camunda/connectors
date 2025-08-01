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
package io.camunda.connector.runtime.core;

import java.util.Set;

public class Keywords {

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
   * A dropdown property that indicates whether the correlation is required for the inbound
   * Connector.
   *
   * <p>This value only exists for inbound Connectors and comes from the extension properties of a
   * BPMN element. It is only present in Message Start Event element templates.
   */
  public static final String CORRELATION_REQUIRED_KEYWORD = "correlationRequired";

  /**
   * The keyword that identifies the source of `correlation key expression` property of a Connector.
   * Correlation key expression is a FEEL expression that is extracts the correlation key from the
   * inbound Connector output.
   *
   * <p>This value only exists for inbound Connectors that target an intermediate message catch
   * event and comes from the extension properties of a BPMN element.
   */
  public static final String CORRELATION_KEY_EXPRESSION_KEYWORD = "correlationKeyExpression";

  public static final String MESSAGE_ID_EXPRESSION = "messageIdExpression";

  public static final String MESSAGE_TTL = "messageTtl";

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
   * The keyword that defines whether connector should reject or consume events that did not lead to
   * a successful activation due to unmatched activation condition.
   *
   * <p>This value only exists for inbound Connectors and comes from the extension properties of a
   * BPMN element.
   */
  public static final String CONSUME_UNMATCHED_EVENTS_KEYWORD = "consumeUnmatchedEvents";

  @Deprecated
  public static final String DEPRECATED_ACTIVATION_CONDITION_KEYWORD =
      "inbound.activationCondition";

  /**
   * The keyword that identifies the source of `type` property of an inbound Connector. Type
   * identifies the specific inbound Connector implementation.
   */
  public static final String INBOUND_TYPE_KEYWORD = "inbound.type";

  /**
   * The keyword that identifies the source of `retry backoff` property of an outbound Connector.
   * Retry backoff is an ISO8601 duration that is submitted to Zeebe with every job failure.
   *
   * <p>This value only exists for outbound Connectors and comes from the job headers.
   */
  public static final String RETRY_BACKOFF_KEYWORD = "retryBackoff";

  /**
   * ID of the boolean flag that indicates whether the deduplication mode is manual or automatic.
   *
   * <p>This value only exists for inbound Connectors and comes from the extension properties of a
   * BPMN element.
   */
  public static final String DEDUPLICATION_MODE_MANUAL_FLAG_KEYWORD = "deduplicationModeManualFlag";

  public static final String DEDUPLICATION_MODE_KEYWORD = "deduplicationMode";

  public enum DeduplicationMode {
    AUTO,
    MANUAL
  }

  public static final String DEDUPLICATION_ID_KEYWORD = "deduplicationId";

  public static final String OPERATION_ID_KEYWORD = "operation";

  /**
   * Properties that are handled by the connector runtime and should not be passed to the inbound
   * connector along with the properties defined by the connector.
   */
  public static final Set<String> INBOUND_RUNTIME_PROPERTIES =
      Set.of(
          INBOUND_TYPE_KEYWORD,
          DEDUPLICATION_MODE_KEYWORD,
          DEDUPLICATION_ID_KEYWORD,
          MESSAGE_ID_EXPRESSION,
          CORRELATION_KEY_EXPRESSION_KEYWORD,
          DEPRECATED_ACTIVATION_CONDITION_KEYWORD,
          ACTIVATION_CONDITION_KEYWORD,
          CONSUME_UNMATCHED_EVENTS_KEYWORD,
          MESSAGE_TTL);

  /**
   * Subset of {@link #INBOUND_RUNTIME_PROPERTIES} that should not be used for connector
   * deduplication
   */
  public static final Set<String> PROPERTIES_EXCLUDED_FROM_DEDUPLICATION =
      Set.of(
          INBOUND_TYPE_KEYWORD,
          DEDUPLICATION_MODE_KEYWORD,
          DEDUPLICATION_ID_KEYWORD,
          MESSAGE_ID_EXPRESSION,
          CORRELATION_KEY_EXPRESSION_KEYWORD,
          DEPRECATED_ACTIVATION_CONDITION_KEYWORD,
          ACTIVATION_CONDITION_KEYWORD,
          MESSAGE_TTL,
          CORRELATION_REQUIRED_KEYWORD,
          DEDUPLICATION_MODE_MANUAL_FLAG_KEYWORD,
          RESULT_EXPRESSION_KEYWORD,
          RESULT_VARIABLE_KEYWORD);
}
