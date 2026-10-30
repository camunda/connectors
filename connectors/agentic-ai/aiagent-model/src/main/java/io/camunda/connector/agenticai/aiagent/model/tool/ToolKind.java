/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.tool;

import com.fasterxml.jackson.annotation.JsonProperty;

/** The class of tool a tool call targets. It is serialized in lower case. */
public enum ToolKind {
  /**
   * A tool that is a BPMN element, activated in the process; its result arrives with a later job.
   */
  @JsonProperty("process")
  PROCESS,
  /** A tool the agent runs itself, without activating a BPMN element. */
  @JsonProperty("runtime")
  RUNTIME
}
