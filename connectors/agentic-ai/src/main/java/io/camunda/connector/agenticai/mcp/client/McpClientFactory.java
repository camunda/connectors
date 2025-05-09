/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import io.camunda.connector.agenticai.autoconfigure.McpClientConfigurationProperties;
import io.camunda.connector.agenticai.autoconfigure.McpClientConfigurationProperties.McpClientConfiguration;
import io.camunda.connector.agenticai.autoconfigure.McpClientConfigurationProperties.McpClientTransportConfiguration;
import java.util.Optional;

public class McpClientFactory {

  public McpClientFactory() {}

  public McpClient createClient(McpClientConfiguration config) {
    final var transport = createTransport(config.stio() != null ? config.stio() : config.http());
    final var builder = new DefaultMcpClient.Builder().transport(transport);

    Optional.ofNullable(config.initializationTimeout()).map(builder::initializationTimeout);
    Optional.ofNullable(config.toolExecutionTimeout()).map(builder::toolExecutionTimeout);
    Optional.ofNullable(config.reconnectInterval()).map(builder::reconnectInterval);

    return builder.build();
  }

  private McpTransport createTransport(McpClientTransportConfiguration transportConfig) {
    return switch (transportConfig) {
      case McpClientConfigurationProperties.StdioMcpClientTransportConfiguration stdio ->
          new StdioMcpTransport.Builder()
              .command(stdio.command())
              .environment(stdio.env())
              .logEvents(stdio.logEvents())
              .build();
      case McpClientConfigurationProperties.HttpMcpClientTransportConfiguration http ->
          new HttpMcpTransport.Builder()
              .sseUrl(http.sseUrl())
              .timeout(http.timeout())
              .logRequests(http.logRequests())
              .logResponses(http.logResponses())
              .build();
    };
  }
}
