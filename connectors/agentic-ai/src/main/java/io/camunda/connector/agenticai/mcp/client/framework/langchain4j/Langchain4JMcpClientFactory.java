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
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import io.camunda.connector.agenticai.mcp.client.McpClientFactory;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.McpClientConfiguration;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.McpClientTransportConfiguration;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.jspecify.annotations.NonNull;

public class Langchain4JMcpClientFactory implements McpClientFactory<McpClient> {

  @Override
  public McpClient createClient(String clientId, McpClientConfiguration config) {
    final var transport = createTransport(resolveTransportConfiguration(clientId, config));
    final var builder = new DefaultMcpClient.Builder().key(clientId).transport(transport);

    Optional.ofNullable(config.initializationTimeout()).map(builder::initializationTimeout);
    Optional.ofNullable(config.toolExecutionTimeout()).map(builder::toolExecutionTimeout);
    Optional.ofNullable(config.reconnectInterval()).map(builder::reconnectInterval);

    return builder.build();
  }

  private McpClientTransportConfiguration resolveTransportConfiguration(
      String clientId, @NonNull McpClientConfiguration config) {
    final var configuredTransports =
        Stream.of(config.stdio(), config.http(), config.sse()).filter(Objects::nonNull).toList();

    if (configuredTransports.size() == 1) {
      return configuredTransports.getFirst();
    }

    if (configuredTransports.isEmpty()) {
      throw new IllegalArgumentException(
          "Missing transport configuration for MCP client '%s'".formatted(clientId));
    } else {
      final var configuredTypes =
          configuredTransports.stream().map(McpClientTransportConfiguration::type).toList();

      throw new IllegalArgumentException(
          "Ambiguous configuration for MCP client '%s'. Multiple transports %s are configured."
              .formatted(clientId, configuredTypes));
    }
  }

  private McpTransport createTransport(McpClientTransportConfiguration transportConfig) {
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

      case McpClientConfigurationProperties.StreamableHttpMcpClientTransportConfiguration http ->
          new StreamableHttpMcpTransport.Builder()
              .url(http.url())
              .timeout(http.timeout())
              .customHeaders(http.headers())
              .logRequests(http.logRequests())
              .logResponses(http.logResponses())
              .build();

      case McpClientConfigurationProperties.SseHttpMcpClientTransportConfiguration sse ->
          new HttpMcpTransport.Builder()
              .sseUrl(sse.url())
              .timeout(sse.timeout())
              .customHeaders(sse.headers())
              .logRequests(sse.logRequests())
              .logResponses(sse.logResponses())
              .build();
    };
  }
}
