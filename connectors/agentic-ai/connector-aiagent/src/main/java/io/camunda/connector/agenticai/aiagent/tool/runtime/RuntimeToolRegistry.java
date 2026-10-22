/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.tool.runtime;

import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeTool;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolContext;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The runtime tools registered as beans. An agent instance created without the runtime tools
 * feature (see {@code AgentFeatures#RUNTIME_TOOLS}) offers none; for every other agent a tool is
 * offered while it is {@link RuntimeTool#isEnabled enabled}.
 */
public interface RuntimeToolRegistry {

  /** The names of all registered tools, whether enabled or not. */
  Set<String> toolNames();

  /** The definitions of the enabled tools in registration order, empty without the feature. */
  List<ToolDefinition> enabledToolDefinitions(RuntimeToolContext context);

  Optional<RuntimeTool> findEnabled(RuntimeToolContext context, String name);
}
