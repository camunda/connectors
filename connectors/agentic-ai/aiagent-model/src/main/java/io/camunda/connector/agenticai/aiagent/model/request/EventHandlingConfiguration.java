/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request;

/**
 * How an agent handles the completion of an event sub-process.
 *
 * @param behavior the behavior on completing an event sub-process
 */
public record EventHandlingConfiguration(EventHandlingBehavior behavior) {

  public enum EventHandlingBehavior {
    WAIT_FOR_TOOL_CALL_RESULTS,
    INTERRUPT_TOOL_CALLS
  }
}
