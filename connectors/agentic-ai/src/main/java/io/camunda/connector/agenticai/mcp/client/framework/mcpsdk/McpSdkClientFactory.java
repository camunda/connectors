/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.mcpsdk;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.mcp.client.McpClientFactory;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties;
import io.camunda.connector.agenticai.mcp.client.execution.McpClientDelegate;
import io.camunda.connector.agenticai.mcp.client.framework.bootstrap.McpClientHeadersSupplierFactory;
import io.camunda.connector.agenticai.mcp.client.framework.mcpsdk.rpc.McpSdkMcpClientDelegate;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

public class McpSdkClientFactory implements McpClientFactory {

  private final ObjectMapper objectMapper;
  private final McpClientHeadersSupplierFactory headersSupplierFactory;

  public McpSdkClientFactory(
      ObjectMapper objectMapper, McpClientHeadersSupplierFactory headersSupplierFactory) {
    this.objectMapper = objectMapper;
    this.headersSupplierFactory = headersSupplierFactory;
  }

  @Override
  public McpClientDelegate createClient(
      String clientId, McpClientConfigurationProperties.McpClientConfiguration config) {
    var clientBuilder =
        McpClient.sync(createTransport(config))
            .capabilities(McpSchema.ClientCapabilities.builder().roots(true).build());

    Optional.ofNullable(config.initializationTimeout()).map(clientBuilder::initializationTimeout);
    Optional.ofNullable(config.toolExecutionTimeout()).map(clientBuilder::requestTimeout);
    // todo reconnect interval?
    // Optional.ofNullable(config.reconnectInterval()).map(clientBuilder::);

    return new McpSdkMcpClientDelegate(clientBuilder.build(), objectMapper);
  }

  private McpClientTransport createTransport(
      McpClientConfigurationProperties.McpClientConfiguration config) {
    return switch (config.transport()) {
      case McpClientConfigurationProperties.SseHttpMcpClientTransportConfiguration
              sseHttpMcpClientTransportConfiguration ->
          createSseTransport(sseHttpMcpClientTransportConfiguration);
      case McpClientConfigurationProperties.StdioMcpClientTransportConfiguration
              stdioMcpClientTransportConfiguration ->
          createStdioTransport(stdioMcpClientTransportConfiguration);
      case McpClientConfigurationProperties.StreamableHttpMcpClientTransportConfiguration
              streamableHttpMcpClientTransportConfiguration ->
          createStreamableHttpTransport(streamableHttpMcpClientTransportConfiguration);
    };
  }

  private StdioClientTransport createStdioTransport(
      McpClientConfigurationProperties.StdioMcpClientTransportConfiguration stdioConfig) {

    // todo timeout?
    return new StdioClientTransport(
        ServerParameters.builder(stdioConfig.command()).args(stdioConfig.args()).build(),
        McpJsonMapper.createDefault());
  }

  private HttpClientStreamableHttpTransport createStreamableHttpTransport(
      McpClientConfigurationProperties.StreamableHttpMcpClientTransportConfiguration
          streamableHttpConfig) {

    return HttpClientStreamableHttpTransport.builder(streamableHttpConfig.url())
        .connectTimeout(timeout(streamableHttpConfig.timeout()))
        .customizeRequest(
            request -> {
              var headers = customHeaders(streamableHttpConfig);
              headers.forEach(request::header);
            })
        // todo logging request/response
        // todo proxy configuration
        .build();
  }

  private HttpClientSseClientTransport createSseTransport(
      McpClientConfigurationProperties.SseHttpMcpClientTransportConfiguration sseConfig) {
    return HttpClientSseClientTransport.builder(sseConfig.url())
        .connectTimeout(timeout(sseConfig.timeout()))
        .customizeRequest(
            request -> {
              var headers = customHeaders(sseConfig);
              headers.forEach(request::header);
            })
        // todo logging request/response
        // todo proxy configuration
        .build();
  }

  private Map<String, String> customHeaders(
      McpClientConfigurationProperties.McpClientHttpTransportConfiguration transportConfiguration) {
    var suppliers = headersSupplierFactory.createHttpHeadersSupplier(transportConfiguration);

    return suppliers.get();
  }

  private Duration timeout(Duration setTimeout) {
    return setTimeout != null ? setTimeout : Duration.ofSeconds(30);
  }
}
