/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.api.chatmodel;

/** Error codes of a failed or rejected model call, reported by the chat model implementations. */
public interface ChatModelErrorCodes {
  String ERROR_CODE_FAILED_MODEL_CALL = "FAILED_MODEL_CALL";
  String ERROR_CODE_UNSUPPORTED_MODEL_CONFIGURATION = "UNSUPPORTED_MODEL_CONFIGURATION";
  String ERROR_CODE_MODEL_RESPONSE_CONTENT_FILTERED = "MODEL_RESPONSE_CONTENT_FILTERED";
  String ERROR_CODE_MODEL_CONTEXT_WINDOW_EXCEEDED = "MODEL_CONTEXT_WINDOW_EXCEEDED";
  String ERROR_CODE_MODEL_RESPONSE_GUARDRAIL_INTERVENED = "MODEL_RESPONSE_GUARDRAIL_INTERVENED";
}
