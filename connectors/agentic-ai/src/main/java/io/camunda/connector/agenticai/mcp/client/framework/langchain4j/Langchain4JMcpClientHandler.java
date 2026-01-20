/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.langchain4j;

import dev.langchain4j.mcp.client.McpClient;
import io.camunda.connector.agenticai.mcp.client.McpClientHandler;
import io.camunda.connector.agenticai.mcp.client.McpClientRegistry;
import io.camunda.connector.agenticai.mcp.client.filters.FilterOptions;
import io.camunda.connector.agenticai.mcp.client.framework.langchain4j.rpc.Langchain4JMcpClientExecutor;
import io.camunda.connector.agenticai.mcp.client.model.McpClientRequest;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientResult;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Langchain4JMcpClientHandler implements McpClientHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(Langchain4JMcpClientHandler.class);

  private final McpClientRegistry<McpClient> clientRegistry;
  private final Langchain4JMcpClientExecutor clientExecutor;

  public Langchain4JMcpClientHandler(
      McpClientRegistry<McpClient> clientRegistry, Langchain4JMcpClientExecutor clientExecutor) {
    this.clientRegistry = clientRegistry;
    this.clientExecutor = clientExecutor;
  }

  @Override
  public McpClientResult handle(OutboundConnectorContext context, McpClientRequest request) {
    final var clientId = request.data().client().clientId();
    final var operation = request.data().connectorMode().toMcpClientOperation();

    LOGGER.debug(
        "MCP({}): Handling operation '{}' on runtime-configured client",
        clientId,
        operation.method());

    final var client = clientRegistry.getClient(clientId);

    final var filterOptions =
        request
            .data()
            .connectorMode()
            .createFilterOptions()
            .orElseGet(FilterOptions::defaultOptions);

    return clientExecutor.execute(client, operation, filterOptions);
  }
}
