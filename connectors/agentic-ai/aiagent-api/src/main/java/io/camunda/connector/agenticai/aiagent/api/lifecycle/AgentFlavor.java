/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.api.lifecycle;

import io.camunda.connector.agenticai.aiagent.api.Experimental;

/** The AI Agent flavor that runs a job. */
@Experimental
public enum AgentFlavor {
  /** AI Agent Task: a service task whose tool-calling loop is modeled explicitly. */
  TASK,
  /**
   * AI Agent Sub-process: an ad-hoc sub-process whose tool-calling loop is driven by the engine.
   */
  SUB_PROCESS
}
