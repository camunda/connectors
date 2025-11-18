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
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.StdioMcpClientTransportConfiguration;
import java.util.ArrayList;
import java.util.Optional;

public class Langchain4JMcpClientFactory implements McpClientFactory<McpClient> {

  private static final LoggingResolver DEFAULT_LOGGING_RESOLVER = (clientId, config) -> false;

  private LoggingResolver logStdioEvents = DEFAULT_LOGGING_RESOLVER;
  private LoggingResolver logHttpRequests = DEFAULT_LOGGING_RESOLVER;
  private LoggingResolver logHttpResponses = DEFAULT_LOGGING_RESOLVER;

  private final Langchain4JMcpClientHeadersSupplierFactory headersSupplierFactory;

  public Langchain4JMcpClientFactory(
      Langchain4JMcpClientHeadersSupplierFactory headersSupplierFactory) {
    this.headersSupplierFactory = headersSupplierFactory;
  }

  @Override
  public McpClient createClient(String clientId, McpClientConfiguration config) {
    final var transport = createTransport(clientId, config);
    final var builder = new DefaultMcpClient.Builder().key(clientId).transport(transport);

    Optional.ofNullable(config.initializationTimeout()).map(builder::initializationTimeout);
    Optional.ofNullable(config.toolExecutionTimeout()).map(builder::toolExecutionTimeout);
    Optional.ofNullable(config.reconnectInterval()).map(builder::reconnectInterval);

    return builder.build();
  }

  private McpTransport createTransport(String clientId, McpClientConfiguration config) {
    return switch (config.transport()) {
      case StdioMcpClientTransportConfiguration stdio -> {
        final var commandParts = new ArrayList<String>();
        commandParts.add(stdio.command());
        commandParts.addAll(stdio.args());

        yield new StdioMcpTransport.Builder()
            .command(commandParts)
            .environment(stdio.env())
            .logEvents(logStdioEvents.resolve(clientId, config))
            .build();
      }

      case McpClientConfigurationProperties.StreamableHttpMcpClientTransportConfiguration http ->
          new StreamableHttpMcpTransport.Builder()
              .url(http.url())
              .timeout(http.timeout())
              .customHeaders(
                  headersSupplierFactory
                      .createHttpHeadersSupplier(http)
                      .get()) // TODO remove .get() call with L4J > 1.8.0
              .logRequests(logHttpRequests.resolve(clientId, config))
              .logResponses(logHttpResponses.resolve(clientId, config))
              .build();

      case McpClientConfigurationProperties.SseHttpMcpClientTransportConfiguration sse ->
          new HttpMcpTransport.Builder()
              .sseUrl(sse.url())
              .timeout(sse.timeout())
              .customHeaders(
                  headersSupplierFactory
                      .createHttpHeadersSupplier(sse)
                      .get()) // TODO remove .get() call with L4J > 1.8.0
              .logRequests(logHttpRequests.resolve(clientId, config))
              .logResponses(logHttpResponses.resolve(clientId, config))
              .build();
    };
  }

  public void logStdioEvents(boolean logStdioEvents) {
    logStdioEvents((clientId, config) -> logStdioEvents);
  }

  public void logStdioEvents(LoggingResolver stdioEventsLoggingResolver) {
    this.logStdioEvents = stdioEventsLoggingResolver;
  }

  public void logHttpRequests(boolean logHttpRequests) {
    logHttpRequests((clientId, config) -> logHttpRequests);
  }

  public void logHttpRequests(LoggingResolver httpRequestsLoggingResolver) {
    this.logHttpRequests = httpRequestsLoggingResolver;
  }

  public void logHttpResponses(boolean logHttpResponses) {
    logHttpResponses((clientId, config) -> logHttpResponses);
  }

  public void logHttpResponses(LoggingResolver httpResponsesLoggingResolver) {
    this.logHttpResponses = httpResponsesLoggingResolver;
  }

  @FunctionalInterface
  public interface LoggingResolver {
    boolean resolve(String clientId, McpClientConfiguration config);
  }
}
