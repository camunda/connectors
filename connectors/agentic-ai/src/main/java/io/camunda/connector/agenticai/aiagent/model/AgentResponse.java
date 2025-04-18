/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import java.util.List;
import java.util.Map;

public record AgentResponse(
    AgentContext context, Map<String, Object> chatResponse, List<ToolToCall> toolsToCall) {
  public record ToolToCall(String id, String name, Map<String, Object> input) {}
}
