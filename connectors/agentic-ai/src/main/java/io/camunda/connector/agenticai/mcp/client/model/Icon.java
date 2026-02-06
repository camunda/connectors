/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.connector.agenticai.model.AgenticAiRecord;
import java.util.List;

/**
 * Icon metadata for MCP resources, tools, and prompts as per MCP 2025-11-25 specification.
 *
 * @param src URL or data URI for the icon
 * @param mimeType MIME type of the icon (e.g., "image/png", "image/svg+xml")
 * @param sizes List of size strings (e.g., "48x48", "any")
 */
@AgenticAiRecord
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Icon(String src, String mimeType, List<String> sizes) {}
