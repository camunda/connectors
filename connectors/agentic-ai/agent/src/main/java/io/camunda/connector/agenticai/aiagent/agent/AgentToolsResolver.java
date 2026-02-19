/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolsSchemaResponse;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;

public interface AgentToolsResolver {
  /** Loads tool definitions for the given execution context. */
  AdHocToolsSchemaResponse loadAdHocToolsSchema(
      AgentExecutionContext executionContext, AgentContext agentContext);

  /**
   * Updates tool definitions after the agent detected a process definition migration, potentially
   * introducing changes in tools.
   */
  AgentContext updateToolDefinitions(
      AgentExecutionContext executionContext, AgentContext agentContext);
}
