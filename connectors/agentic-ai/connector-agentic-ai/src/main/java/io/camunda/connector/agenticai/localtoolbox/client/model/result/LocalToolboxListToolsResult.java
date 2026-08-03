/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.localtoolbox.client.model.result;

import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import java.util.List;

/**
 * Result of a {@code tools/list} operation: the tool schema resolved by introspecting the toolbox
 * process's ad-hoc sub-process, reusing {@link ToolDefinition} directly since discovery goes
 * through the same {@code AdHocToolsSchemaResolver} the calling agent uses for its own tools.
 */
public record LocalToolboxListToolsResult(List<ToolDefinition> toolDefinitions)
    implements LocalToolboxClientResult {}
