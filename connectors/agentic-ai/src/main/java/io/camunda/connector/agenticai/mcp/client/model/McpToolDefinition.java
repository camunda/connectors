/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.model;

import io.camunda.connector.agenticai.model.AgenticAiRecord;
import jakarta.annotation.Nullable;
import java.util.Map;

@AgenticAiRecord
public record McpToolDefinition(
    String name,
    @Nullable String title,
    @Nullable String description,
    Map<String, Object> inputSchema) {}
