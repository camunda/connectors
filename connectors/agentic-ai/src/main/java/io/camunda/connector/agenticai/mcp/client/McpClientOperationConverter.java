/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client;

import io.camunda.connector.agenticai.mcp.client.model.McpClientOperation;
import io.camunda.connector.agenticai.mcp.client.model.McpClientOperationConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpConnectorModeConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpConnectorModeConfiguration.StandaloneModeConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpConnectorModeConfiguration.ToolModeConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpStandaloneOperationConfiguration;
import java.util.Collections;

public class McpClientOperationConverter {

  public McpClientOperation convertOperation(McpConnectorModeConfiguration connectorMode) {
    return switch (connectorMode) {
      case ToolModeConfiguration toolMode -> convertToolModeOperation(toolMode.toolOperation());
      case StandaloneModeConfiguration standaloneMode ->
          convertStandaloneModeOperation(standaloneMode.operation());
    };
  }

  private McpClientOperation convertToolModeOperation(
      McpClientOperationConfiguration toolModeOperation) {
    return McpClientOperation.of(toolModeOperation.method(), toolModeOperation.params());
  }

  private McpClientOperation convertStandaloneModeOperation(
      McpStandaloneOperationConfiguration standaloneOperation) {
    return McpClientOperation.of(
        standaloneOperation.method(),
        standaloneOperation.parameters().orElseGet(Collections::emptyMap));
  }
}
