/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.langchain4j;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.mcp.client.McpClient;
import io.camunda.connector.agenticai.mcp.client.McpClientFactory;
import io.camunda.connector.agenticai.mcp.client.McpClientRemoteHandler;
import io.camunda.connector.agenticai.mcp.client.McpToolNameFilter;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.HttpMcpClientTransportConfiguration;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.McpClientConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpClientOperation;
import io.camunda.connector.agenticai.mcp.client.model.McpClientRemoteRequest;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientResult;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Langchain4JMcpClientRemoteHandler implements McpClientRemoteHandler {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(Langchain4JMcpClientRemoteHandler.class);

  private final ObjectMapper objectMapper;
  private final McpClientFactory<McpClient> clientFactory;
  private final Langchain4JMcpClientExecutor clientExecutor;

  public Langchain4JMcpClientRemoteHandler(
      ObjectMapper objectMapper,
      McpClientFactory<McpClient> clientFactory,
      Langchain4JMcpClientExecutor clientExecutor) {
    this.objectMapper = objectMapper;
    this.clientFactory = clientFactory;
    this.clientExecutor = clientExecutor;
  }

  @Override
  public McpClientResult handle(OutboundConnectorContext context, McpClientRemoteRequest request) {
    final var clientId =
        "%s_%s"
            .formatted(
                context.getJobContext().getElementId(),
                context.getJobContext().getElementInstanceKey());
    final var operation =
        objectMapper.convertValue(request.data().operation(), McpClientOperation.class);
    final var toolNameFilter = McpToolNameFilter.from(request.data().tools());

    final var configuration = createClientConfiguration(request);

    LOGGER.debug(
        "MCP({}): Creating temporary HTTP client for operation '{}'", clientId, operation.method());

    // TODO add some kind of pooling/connection reuse instead of opening/closing the MCP connection
    // for each request
    try (final var client = clientFactory.createClient(clientId, configuration)) {
      return clientExecutor.execute(client, operation, toolNameFilter);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private McpClientConfiguration createClientConfiguration(McpClientRemoteRequest request) {
    final var connection = request.data().connection();
    final var httpTransportConfiguration =
        new HttpMcpClientTransportConfiguration(
            connection.sseUrl(), connection.headers(), connection.timeout(), false, false);

    return new McpClientConfiguration(true, null, httpTransportConfiguration, null, null, null);
  }
}
