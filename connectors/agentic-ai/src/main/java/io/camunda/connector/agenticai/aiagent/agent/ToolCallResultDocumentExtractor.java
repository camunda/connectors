/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.api.document.Document;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

/**
 * Extracts {@link Document} instances from a list of tool call results, grouped by tool call.
 *
 * <p>The actual extraction strategy is delegated to the {@link GatewayToolHandlerRegistry}: each
 * tool call result is routed to its responsible {@code GatewayToolHandler} (which may walk a typed
 * domain object), with the generic content-tree walker as the default fallback for tool calls not
 * managed by any gateway handler.
 */
public class ToolCallResultDocumentExtractor {

  /** Documents extracted from a single tool call result, grouped with the tool call identity. */
  public record ToolCallDocuments(
      String toolCallId, String toolCallName, List<Document> documents) {}

  private final GatewayToolHandlerRegistry gatewayToolHandlers;

  public ToolCallResultDocumentExtractor(GatewayToolHandlerRegistry gatewayToolHandlers) {
    this.gatewayToolHandlers = gatewayToolHandlers;
  }

  /**
   * Extracts all {@link Document} instances from the given tool call results, grouped by tool call.
   * Tool calls without documents are excluded from the result. Order is preserved.
   */
  public List<ToolCallDocuments> extractDocuments(List<ToolCallResult> toolCallResults) {
    final var result = new ArrayList<ToolCallDocuments>();

    for (ToolCallResult toolCallResult : toolCallResults) {
      final var documents = gatewayToolHandlers.extractDocuments(toolCallResult);
      if (!documents.isEmpty()) {
        result.add(
            new ToolCallDocuments(
                StringUtils.defaultString(toolCallResult.id()),
                StringUtils.defaultIfBlank(toolCallResult.name(), "unknown"),
                documents));
      }
    }

    return result;
  }
}
