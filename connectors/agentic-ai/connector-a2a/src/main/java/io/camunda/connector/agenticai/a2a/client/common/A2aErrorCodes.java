/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.common;

public interface A2aErrorCodes {
  String ERROR_CODE_A2A_CLIENT_SEND_MESSAGE_RESPONSE_TIMEOUT =
      "A2A_CLIENT_SEND_MESSAGE_RESPONSE_TIMEOUT";
  String ERROR_CODE_A2A_CLIENT_AGENT_CARD_RETRIEVAL_FAILED =
      "ERROR_CODE_A2A_CLIENT_AGENT_CARD_RETRIEVAL_FAILED";
  String ERROR_CODE_A2A_CLIENT_SEND_MESSAGE_FAILED = "A2A_CLIENT_SEND_MESSAGE_FAILED";
  String ERROR_CODE_A2A_CLIENT_TASK_RETRIEVAL_FAILED = "A2A_CLIENT_TASK_RETRIEVAL_FAILED";
}
