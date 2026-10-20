/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.model.content;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.connector.api.document.Document;
import java.util.Map;

/**
 * Represents MCP content that has been converted to a Camunda Document.
 *
 * <p>This is produced when binary MCP content (e.g. {@link McpBlobContent}) is stored as a Camunda
 * document.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record McpDocumentContent(
    Document document, @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> metadata)
    implements McpContent {
  public McpDocumentContent {
    if (document == null) {
      throw new IllegalArgumentException("Document cannot be null");
    }
  }
}
