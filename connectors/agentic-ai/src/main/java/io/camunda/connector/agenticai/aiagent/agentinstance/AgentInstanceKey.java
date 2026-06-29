/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agentinstance;

import io.camunda.connector.agenticai.aiagent.model.AgentMetadata;
import org.jspecify.annotations.Nullable;

public record AgentInstanceKey(long value) {

  public static AgentInstanceKey of(long value) {
    return new AgentInstanceKey(value);
  }

  /**
   * Extracts the agent instance key from the given metadata, or {@code null} when the metadata or
   * its key is absent (e.g. agents that pre-date the agent-instance feature).
   */
  public static @Nullable AgentInstanceKey from(@Nullable AgentMetadata metadata) {
    if (metadata == null || metadata.agentInstanceKey() == null) {
      return null;
    }
    return new AgentInstanceKey(metadata.agentInstanceKey());
  }
}
