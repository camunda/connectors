/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.model.message;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Map;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "role")
@JsonSubTypes({
  @JsonSubTypes.Type(value = SystemMessage.class, name = "system"),
  @JsonSubTypes.Type(value = AssistantMessage.class, name = "assistant"),
  @JsonSubTypes.Type(value = UserMessage.class, name = "user"),
  @JsonSubTypes.Type(value = ToolCallResultMessage.class, name = "tool_call_result"),
})
public interface Message {
  Map<String, Object> metadata();
}
