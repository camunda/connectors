/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

public interface AgentErrorCodes {
  String ERROR_CODE_NO_USER_MESSAGE_CONTENT = "NO_USER_MESSAGE_CONTENT";
  String ERROR_CODE_TOOL_CALL_RESULTS_ON_EMPTY_CONTEXT = "TOOL_CALL_RESULTS_ON_EMPTY_CONTEXT";
  String ERROR_CODE_MAXIMUM_NUMBER_OF_MODEL_CALLS_REACHED = "MAXIMUM_NUMBER_OF_MODEL_CALLS_REACHED";
  String ERROR_CODE_FAILED_TO_PARSE_RESPONSE_CONTENT = "FAILED_TO_PARSE_RESPONSE_CONTENT";
  String ERROR_CODE_FAILED_MODEL_CALL = "FAILED_MODEL_CALL";
  String ERROR_CODE_MIGRATION_MISSING_TOOLS = "MIGRATION_MISSING_TOOLS";
  String ERROR_CODE_MIGRATION_GATEWAY_TOOL_DEFINITIONS_CHANGED =
      "MIGRATION_GATEWAY_TOOL_DEFINITIONS_CHANGED";
}
