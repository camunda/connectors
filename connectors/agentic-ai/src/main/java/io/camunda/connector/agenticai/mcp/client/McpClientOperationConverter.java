/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client;

import io.camunda.connector.agenticai.mcp.client.model.McpClientOperation;
import io.camunda.connector.agenticai.mcp.client.model.McpConnectorModeConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpConnectorModeConfiguration.StandaloneModeConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpConnectorModeConfiguration.ToolModeConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpStandaloneOperationConfiguration;

public class McpClientOperationConverter {

  public McpClientOperation convertOperation(McpConnectorModeConfiguration connectorMode) {
    return switch (connectorMode) {
      case ToolModeConfiguration toolMode -> convertToolModeOperation(toolMode);

      case StandaloneModeConfiguration standaloneMode ->
          convertStandaloneModeOperation(standaloneMode.operation());
    };
  }

  private McpClientOperation convertToolModeOperation(ToolModeConfiguration toolMode) {
    final var toolOperation = toolMode.toolOperation();

    return McpClientOperation.withParams(toolOperation.method(), toolOperation.params());
  }

  private McpClientOperation convertStandaloneModeOperation(
      McpStandaloneOperationConfiguration operation) {
    return switch (operation) {
      case McpStandaloneOperationConfiguration.McpNoArgumentsOperation noArgumentsOperation ->
          McpClientOperation.withoutParams(noArgumentsOperation.method());
      case McpStandaloneOperationConfiguration.McpArgumentsOperation withArgumentsOperation ->
          McpClientOperation.withParams(
              withArgumentsOperation.method(), withArgumentsOperation.parameters());
      default -> throw new IllegalStateException("Unexpected operation type: " + operation);
    };
  }
}
