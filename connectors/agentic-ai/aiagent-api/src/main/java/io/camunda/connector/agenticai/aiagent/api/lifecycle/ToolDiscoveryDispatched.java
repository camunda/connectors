/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.api.lifecycle;

import io.camunda.connector.agenticai.aiagent.api.Experimental;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import java.util.List;

/**
 * The job dispatches gateway tool discovery calls and persists the agent context.
 *
 * @param toolCalls the gateway tool discovery tool calls the job issues
 */
@Experimental
public record ToolDiscoveryDispatched(List<ToolCall> toolCalls) {}
