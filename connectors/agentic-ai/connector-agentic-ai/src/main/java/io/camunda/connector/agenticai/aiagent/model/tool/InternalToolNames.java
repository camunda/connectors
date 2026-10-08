/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.tool;

/**
 * The names of the tools the agent offers and runs itself, the local tools, share one reserved
 * prefix. A BPMN element of an agent that has the documents feature must not use a tool name that
 * starts with it, and the names of gateway tools (such as {@code MCP_...} and {@code A2A_...})
 * never do.
 */
public final class InternalToolNames {

  /** The reserved prefix of the names of the tools the agent runs itself. */
  public static final String PREFIX = "c8__";

  private InternalToolNames() {}

  /** Returns {@code true} if the tool name starts with the reserved {@link #PREFIX}. */
  public static boolean isReserved(String toolName) {
    return toolName.startsWith(PREFIX);
  }
}
