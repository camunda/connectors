/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.mcp.client.model.McpClientOperation;
import io.camunda.connector.agenticai.mcp.client.model.McpConnectorModeConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpStandaloneOperationConfiguration;

public class McpClientOperationConverter {

  private final ObjectMapper objectMapper;

  public McpClientOperationConverter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public McpClientOperation convertOperation(McpConnectorModeConfiguration connectorMode) {
    return switch (connectorMode) {
      case McpConnectorModeConfiguration.ToolModeConfiguration toolMode ->
          objectMapper.convertValue(toolMode.toolOperation(), McpClientOperation.class);

      case McpConnectorModeConfiguration.StandaloneModeConfiguration standaloneMode ->
          convertStandaloneToOperation(standaloneMode.operation());
    };
  }

  private McpClientOperation convertStandaloneToOperation(
      McpStandaloneOperationConfiguration operation) {
    return switch (operation) {
      case McpStandaloneOperationConfiguration.ListToolsOperationConfiguration ignored ->
          new McpClientOperation.McpClientListToolsOperation();
      case McpStandaloneOperationConfiguration.CallToolOperationConfiguration callTool ->
          new McpClientOperation.McpClientCallToolOperation(
              new McpClientOperation.McpClientCallToolOperation.McpClientCallToolOperationParams(
                  callTool.toolName(), callTool.toolArguments()));
    };
  }
}
