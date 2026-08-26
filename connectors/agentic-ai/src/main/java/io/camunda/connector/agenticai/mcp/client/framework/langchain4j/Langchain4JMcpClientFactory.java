/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.langchain4j;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import io.camunda.connector.agenticai.mcp.client.McpClientFactory;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.McpClientConfiguration;
import java.util.ArrayList;
import java.util.Optional;

public class Langchain4JMcpClientFactory implements McpClientFactory<McpClient> {

  @Override
  public McpClient createClient(String clientId, McpClientConfiguration config) {
    final var transportConfig = config.stdio() != null ? config.stdio() : config.sse();
    final var transport = createTransport(transportConfig);
    final var builder =
        new DefaultMcpClient.Builder()
            .key(clientId)
            .transport(transport)
            .protocolVersion(protocolVersion(transportConfig));

    Optional.ofNullable(config.initializationTimeout()).map(builder::initializationTimeout);
    Optional.ofNullable(config.toolExecutionTimeout()).map(builder::toolExecutionTimeout);
    Optional.ofNullable(config.reconnectInterval()).map(builder::reconnectInterval);

    return builder.build();
  }

  /**
   * HttpMcpTransport implements the legacy HTTP+SSE transport (MCP protocol revision 2024-11-05).
   * DefaultMcpClient otherwise advertises a much newer default revision, which legacy SSE servers
   * reject/ignore, stalling initialization. Pin the negotiated revision for the SSE transport;
   * stdio keeps the client default (null -> builder default).
   */
  private String protocolVersion(
      McpClientConfigurationProperties.McpClientTransportConfiguration transportConfig) {
    return switch (transportConfig) {
      case McpClientConfigurationProperties.SseHttpMcpClientTransportConfiguration sse ->
          "2024-11-05";
      default -> null;
    };
  }

  private McpTransport createTransport(
      McpClientConfigurationProperties.McpClientTransportConfiguration transportConfig) {
    return switch (transportConfig) {
      case McpClientConfigurationProperties.StdioMcpClientTransportConfiguration stdio -> {
        final var commandParts = new ArrayList<String>();
        commandParts.add(stdio.command());
        commandParts.addAll(stdio.args());

        yield new StdioMcpTransport.Builder()
            .command(commandParts)
            .environment(stdio.env())
            .logEvents(stdio.logEvents())
            .build();
      }
      case McpClientConfigurationProperties.SseHttpMcpClientTransportConfiguration http ->
          new HttpMcpTransport.Builder()
              .sseUrl(http.url())
              .timeout(http.timeout())
              .logRequests(http.logRequests())
              .logResponses(http.logResponses())
              .build();
    };
  }
}
