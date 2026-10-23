/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The key of an agent instance created on the engine. Serializes as a bare JSON number.
 *
 * @param value the key assigned by the engine
 */
public record AgentInstanceKey(@JsonValue long value) {

  @JsonCreator
  public static AgentInstanceKey of(long value) {
    return new AgentInstanceKey(value);
  }
}
