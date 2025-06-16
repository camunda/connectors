/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.langchain4j;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.mcp.client.McpClient;
import io.camunda.connector.agenticai.mcp.client.McpRemoteClientHandler;
import io.camunda.connector.agenticai.mcp.client.McpRemoteClientRegistry;
import io.camunda.connector.agenticai.mcp.client.McpRemoteClientRegistry.McpRemoteClientIdentifier;
import io.camunda.connector.agenticai.mcp.client.McpToolNameFilter;
import io.camunda.connector.agenticai.mcp.client.model.McpClientOperation;
import io.camunda.connector.agenticai.mcp.client.model.McpRemoteClientRequest;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientResult;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Langchain4JMcpRemoteClientHandler implements McpRemoteClientHandler {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(Langchain4JMcpRemoteClientHandler.class);

  private final ObjectMapper objectMapper;
  private final McpRemoteClientRegistry<McpClient> remoteClientRegistry;
  private final Langchain4JMcpClientExecutor clientExecutor;

  public Langchain4JMcpRemoteClientHandler(
      ObjectMapper objectMapper,
      McpRemoteClientRegistry<McpClient> remoteClientRegistry,
      Langchain4JMcpClientExecutor clientExecutor) {
    this.objectMapper = objectMapper;
    this.remoteClientRegistry = remoteClientRegistry;
    this.clientExecutor = clientExecutor;
  }

  @Override
  public McpClientResult handle(OutboundConnectorContext context, McpRemoteClientRequest request) {
    final var clientId = McpRemoteClientIdentifier.from(context);
    final var operation =
        objectMapper.convertValue(request.data().operation(), McpClientOperation.class);
    final var toolNameFilter = McpToolNameFilter.from(request.data().tools());

    LOGGER.debug("MCP({}): Handling operation '{}' on remote client", clientId, operation.method());

    final var client = remoteClientRegistry.getClient(clientId, request.data().connection());

    return clientExecutor.execute(client, operation, toolNameFilter);
  }
}
