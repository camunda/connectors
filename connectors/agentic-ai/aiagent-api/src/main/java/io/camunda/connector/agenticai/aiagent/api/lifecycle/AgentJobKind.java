/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.api.lifecycle;

import io.camunda.connector.agenticai.aiagent.api.Experimental;

/** What a job does, known once the agent is initialized. */
@Experimental
public enum AgentJobKind {
  /** The job dispatches gateway tool discovery calls and completes. */
  TOOL_DISCOVERY,
  /** Tool discovery was dispatched earlier and its results are still incomplete. */
  DISCOVERY_PENDING,
  /**
   * The job is ready to converse. It either runs a turn, or completes without one when it only
   * collects partial tool results.
   */
  CONVERSATION
}
