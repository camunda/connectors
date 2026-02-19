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
import io.camunda.connector.http.client.client.jdk.proxy.JdkHttpClientProxyConfigurator;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.ProtocolVersions;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

public class McpSdkClientFactory implements McpClientFactory {

  public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

  private final ObjectMapper objectMapper;
  private final JdkHttpClientProxyConfigurator proxyConfigurator;
  private final McpClientHeadersSupplierFactory headersSupplierFactory;

  public McpSdkClientFactory(
      ObjectMapper objectMapper,
      JdkHttpClientProxyConfigurator proxyConfigurator,
      McpClientHeadersSupplierFactory headersSupplierFactory) {
    this.objectMapper = objectMapper;
    this.proxyConfigurator = proxyConfigurator;
    this.headersSupplierFactory = headersSupplierFactory;
  }

  @Override
  public McpClientDelegate createClient(
      String clientId, McpClientConfigurationProperties.McpClientConfiguration config) {
    var clientBuilder =
        McpClient.sync(createTransport(config))
            .clientInfo(new McpSchema.Implementation("Camunda 8 MCP Connector", "1.0.0"))
            .capabilities(McpSchema.ClientCapabilities.builder().roots(false).build());

    Optional.ofNullable(config.initializationTimeout()).map(clientBuilder::initializationTimeout);
    Optional.ofNullable(config.toolExecutionTimeout()).map(clientBuilder::requestTimeout);

    return new McpSdkMcpClientDelegate(clientId, clientBuilder.build(), objectMapper);
  }

  private McpClientTransport createTransport(
      McpClientConfigurationProperties.McpClientConfiguration config) {
    return switch (config.transport()) {
      case McpClientConfigurationProperties.StdioMcpClientTransportConfiguration
              stdioMcpClientTransportConfiguration ->
          createStdioTransport(stdioMcpClientTransportConfiguration);
      case McpClientConfigurationProperties.StreamableHttpMcpClientTransportConfiguration
              streamableHttpMcpClientTransportConfiguration ->
          createStreamableHttpTransport(streamableHttpMcpClientTransportConfiguration);
      case McpClientConfigurationProperties.SseHttpMcpClientTransportConfiguration
              sseHttpMcpClientTransportConfiguration ->
          createSseTransport(sseHttpMcpClientTransportConfiguration);
    };
  }

  private StdioClientTransport createStdioTransport(
      McpClientConfigurationProperties.StdioMcpClientTransportConfiguration stdioConfig) {

    return new StdioClientTransport(
        ServerParameters.builder(stdioConfig.command()).args(stdioConfig.args()).build(),
        McpJsonMapper.createDefault());
  }

  private HttpClientStreamableHttpTransport createStreamableHttpTransport(
      McpClientConfigurationProperties.StreamableHttpMcpClientTransportConfiguration
          streamableHttpConfig) {
    var headerSupplier = headersSupplierFactory.createHttpHeadersSupplier(streamableHttpConfig);

    return HttpClientStreamableHttpTransport.builder(streamableHttpConfig.url())
        .endpoint(
            streamableHttpConfig.url()) // see https://github.com/camunda/connectors/issues/6393
        .customizeClient(proxyConfigurator::configure)
        .connectTimeout(timeout(streamableHttpConfig.timeout()))
        .supportedProtocolVersions(
            List.of(
                ProtocolVersions.MCP_2024_11_05,
                ProtocolVersions.MCP_2025_03_26,
                ProtocolVersions.MCP_2025_06_18,
                ProtocolVersions.MCP_2025_11_25))
        .customizeRequest(
            request -> {
              var headers = headerSupplier.get();
              headers.forEach(request::header);
            })
        .build();
  }

  private HttpClientSseClientTransport createSseTransport(
      McpClientConfigurationProperties.SseHttpMcpClientTransportConfiguration sseConfig) {
    var headerSuppliers = headersSupplierFactory.createHttpHeadersSupplier(sseConfig);

    return HttpClientSseClientTransport.builder(sseConfig.url())
        .sseEndpoint(sseConfig.url()) // see https://github.com/camunda/connectors/issues/6393
        .customizeClient(proxyConfigurator::configure)
        .connectTimeout(timeout(sseConfig.timeout()))
        .customizeRequest(
            request -> {
              var headers = headerSuppliers.get();
              headers.forEach(request::header);
            })
        .build();
  }

  private Duration timeout(Duration setTimeout) {
    return setTimeout != null ? setTimeout : DEFAULT_TIMEOUT;
  }
}
