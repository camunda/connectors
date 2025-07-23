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
import java.util.Optional;

public class Langchain4JMcpClientFactory implements McpClientFactory<McpClient> {

  @Override
  public McpClient createClient(String clientId, McpClientConfiguration config) {
    final var transport = createTransport(config.stdio() != null ? config.stdio() : config.http());
    final var builder = new DefaultMcpClient.Builder().key(clientId).transport(transport);

    Optional.ofNullable(config.initializationTimeout()).map(builder::initializationTimeout);
    Optional.ofNullable(config.toolExecutionTimeout()).map(builder::toolExecutionTimeout);
    Optional.ofNullable(config.reconnectInterval()).map(builder::reconnectInterval);

    return builder.build();
  }

  private McpTransport createTransport(
      McpClientConfigurationProperties.McpClientTransportConfiguration transportConfig) {
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
