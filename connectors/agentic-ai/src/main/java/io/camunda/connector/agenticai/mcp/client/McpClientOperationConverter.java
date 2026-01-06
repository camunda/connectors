/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.mcp.client.model.McpClientOperation;
import io.camunda.connector.agenticai.mcp.client.model.McpClientOperation.McpClientCallToolOperation;
import io.camunda.connector.agenticai.mcp.client.model.McpClientOperation.McpClientCallToolOperation.McpClientCallToolOperationParams;
import io.camunda.connector.agenticai.mcp.client.model.McpClientOperation.McpClientListResourcesOperation;
import io.camunda.connector.agenticai.mcp.client.model.McpClientOperation.McpClientListToolsOperation;
import io.camunda.connector.agenticai.mcp.client.model.McpConnectorModeConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpConnectorModeConfiguration.StandaloneModeConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpConnectorModeConfiguration.ToolModeConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpStandaloneOperationConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpStandaloneOperationConfiguration.CallToolOperationConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpStandaloneOperationConfiguration.ListToolsOperationConfiguration;
import io.camunda.connector.api.error.ConnectorException;
import java.util.List;

public class McpClientOperationConverter {

  private static final String ERROR_CODE_MCP_CLIENT_UNSUPPORTED_OPERATION =
      "MCP_CLIENT_UNSUPPORTED_OPERATION";
  private static final String ERROR_CODE_MCP_CLIENT_INVALID_PARAMS = "MCP_CLIENT_INVALID_PARAMS";

  private static final List<String> SUPPORTED_OPERATIONS =
      List.of(McpClientListToolsOperation.METHOD, McpClientCallToolOperation.METHOD);

  private final ObjectMapper objectMapper;

  public McpClientOperationConverter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public McpClientOperation convertOperation(McpConnectorModeConfiguration connectorMode) {
    return switch (connectorMode) {
      case ToolModeConfiguration toolMode -> convertToolModeOperation(toolMode);

      case StandaloneModeConfiguration standaloneMode ->
          convertStandaloneModeOperation(standaloneMode.operation());
    };
  }

  private McpClientOperation convertToolModeOperation(ToolModeConfiguration toolMode) {
    final var toolOperation = toolMode.toolOperation();

    return switch (toolOperation.method()) {
      case McpClientListToolsOperation.METHOD -> new McpClientListToolsOperation();

      case McpClientCallToolOperation.METHOD -> {
        try {
          final var params =
              objectMapper.convertValue(
                  toolOperation.params(), McpClientCallToolOperationParams.class);
          yield new McpClientCallToolOperation(params);
        } catch (Exception e) {
          throw new ConnectorException(
              ERROR_CODE_MCP_CLIENT_INVALID_PARAMS,
              "Unable to convert parameters passed to MCP client: %s".formatted(e.getMessage()));
        }
      }

      default ->
          throw new ConnectorException(
              ERROR_CODE_MCP_CLIENT_UNSUPPORTED_OPERATION,
              String.format(
                  "Unsupported MCP operation '%s'. Supported operations: '%s'",
                  toolOperation.method(), String.join("', '", SUPPORTED_OPERATIONS)));
    };
  }

  private McpClientOperation convertStandaloneModeOperation(
      McpStandaloneOperationConfiguration operation) {
    return switch (operation) {
      case ListToolsOperationConfiguration ignored -> new McpClientListToolsOperation();
      case CallToolOperationConfiguration callTool ->
          new McpClientCallToolOperation(
              new McpClientCallToolOperationParams(callTool.toolName(), callTool.toolArguments()));
      case McpStandaloneOperationConfiguration.ListResourcesOperationConfiguration ignored ->
          new McpClientListResourcesOperation();
      case McpStandaloneOperationConfiguration.ListResourceTemplatesOperationConfiguration
              ignored ->
          null;
    };
  }
}
