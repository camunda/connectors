/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.message;

import com.fasterxml.jackson.annotation.JsonValue;

public enum MessageRole {
  SYSTEM("system"),
  USER("user"),
  ASSISTANT("assistant"),
  TOOL_CALL_RESULT("tool_call_result");

  private final String value;

  MessageRole(String value) {
    this.value = value;
  }

  @JsonValue
  public String value() {
    return value;
  }
}
