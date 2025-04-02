/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agents.aiagent.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public record AgentContext(AgentMetrics metrics, List<Map<String, Object>> history) {
  public AgentContext withMetrics(AgentMetrics metrics) {
    return new AgentContext(metrics, history);
  }

  public AgentContext withHistory(List<Map<String, Object>> history) {
    return new AgentContext(metrics, history);
  }

  public static AgentContext empty() {
    return new AgentContext(AgentMetrics.empty(), Collections.emptyList());
  }
}
