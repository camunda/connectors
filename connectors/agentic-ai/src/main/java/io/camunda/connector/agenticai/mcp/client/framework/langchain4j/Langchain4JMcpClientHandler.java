/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.langchain4j;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.mcp.client.McpClient;
import io.camunda.connector.agenticai.mcp.client.McpClientHandler;
import io.camunda.connector.agenticai.mcp.client.McpClientRegistry;
import io.camunda.connector.agenticai.mcp.client.McpToolNameFilter;
import io.camunda.connector.agenticai.mcp.client.model.McpClientOperation;
import io.camunda.connector.agenticai.mcp.client.model.McpClientRequest;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientResult;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Langchain4JMcpClientHandler implements McpClientHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(Langchain4JMcpClientHandler.class);

  private final ObjectMapper objectMapper;
  private final McpClientRegistry<McpClient> clientRegistry;
  private final Langchain4JMcpClientExecutor clientExecutor;

  public Langchain4JMcpClientHandler(
      ObjectMapper objectMapper,
      McpClientRegistry<McpClient> clientRegistry,
      Langchain4JMcpClientExecutor clientExecutor) {
    this.objectMapper = objectMapper;
    this.clientRegistry = clientRegistry;
    this.clientExecutor = clientExecutor;
  }

  @Override
  public McpClientResult handle(OutboundConnectorContext context, McpClientRequest request) {
    final var clientId = request.data().client().clientId();
    final var operation =
        objectMapper.convertValue(request.data().operation(), McpClientOperation.class);
    final var toolNameFilter = McpToolNameFilter.from(request.data().tools());

    LOGGER.debug(
        "MCP({}): Handling operation '{}' on runtime-configured client",
        clientId,
        operation.method());

    final var client = clientRegistry.getClient(clientId);

    return clientExecutor.execute(client, operation, toolNameFilter);
  }
}
