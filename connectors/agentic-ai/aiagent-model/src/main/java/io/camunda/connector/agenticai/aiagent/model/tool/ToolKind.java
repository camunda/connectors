/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.tool;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Who answers a tool call. It is serialized in lower case, and set once when the assistant message
 * with the call enters the conversation.
 */
public enum ToolKind {
  /**
   * A tool that is a BPMN element, activated in the process; its result arrives with a later job.
   */
  @JsonProperty("process")
  PROCESS,
  /**
   * A call the connector runtime answers itself, without activating a BPMN element: the call of a
   * tool a runtime tool owns the name of, or the call of a tool of the process that was rejected
   * for its document parameters and is answered with an error result.
   */
  @JsonProperty("runtime")
  RUNTIME
}
