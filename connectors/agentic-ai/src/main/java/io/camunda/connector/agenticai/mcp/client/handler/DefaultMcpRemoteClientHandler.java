/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.handler;

import io.camunda.connector.agenticai.mcp.client.McpRemoteClientRegistry;
import io.camunda.connector.agenticai.mcp.client.McpRemoteClientRegistry.McpRemoteClientIdentifier;
import io.camunda.connector.agenticai.mcp.client.execution.McpClientDelegate;
import io.camunda.connector.agenticai.mcp.client.execution.McpClientExecutor;
import io.camunda.connector.agenticai.mcp.client.filters.FilterOptions;
import io.camunda.connector.agenticai.mcp.client.model.McpRemoteClientOptionsConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpRemoteClientRequest;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientResult;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultMcpRemoteClientHandler implements McpRemoteClientHandler {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(DefaultMcpRemoteClientHandler.class);

  private final McpRemoteClientRegistry remoteClientRegistry;
  private final McpClientExecutor clientExecutor;

  public DefaultMcpRemoteClientHandler(
      McpRemoteClientRegistry remoteClientRegistry,
      McpClientExecutor clientExecutor) {
    this.remoteClientRegistry = remoteClientRegistry;
    this.clientExecutor = clientExecutor;
  }

  @Override
  public McpClientResult handle(OutboundConnectorContext context, McpRemoteClientRequest request) {
    final var clientId = McpRemoteClientIdentifier.from(context);
    final var cacheable =
        Optional.ofNullable(request.data().options())
            .map(McpRemoteClientOptionsConfiguration::clientCache)
            .orElse(false);
    final var operation = request.data().connectorMode().toMcpClientOperation();

    LOGGER.debug("MCP({}): Handling operation '{}' on remote client", clientId, operation.method());

    McpClientDelegate client = null;

    try {
      final var filterOptions =
          request
              .data()
              .connectorMode()
              .createFilterOptions()
              .orElseGet(FilterOptions::defaultOptions);

      client = remoteClientRegistry.getClient(clientId, request.data().transport(), cacheable);
      return clientExecutor.execute(client, operation, filterOptions);
    } finally {
      if (!cacheable && client != null) {
        remoteClientRegistry.closeClient(clientId, client);
      }
    }
  }
}
