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
import io.camunda.connector.agenticai.mcp.client.framework.mcpsdk.inmemory.InMemoryMcpServer;
import io.camunda.connector.agenticai.mcp.client.framework.mcpsdk.inmemory.InMemoryMcpServerResolver;
import io.camunda.connector.agenticai.mcp.client.framework.mcpsdk.inmemory.InMemoryMcpTransport;
import io.camunda.connector.agenticai.mcp.client.framework.mcpsdk.rpc.McpSdkMcpClientDelegate;
import io.camunda.connector.http.client.client.jdk.proxy.JdkHttpClientProxyConfigurator;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.ProtocolVersions;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public class McpSdkClientFactory implements McpClientFactory {

  public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

  private final ObjectMapper objectMapper;
  private final JdkHttpClientProxyConfigurator proxyConfigurator;
  private final McpClientHeadersSupplierFactory headersSupplierFactory;
  private final @Nullable InMemoryMcpServerResolver inMemoryServerResolver;

  /** Creates a factory that does not support {@code IN_MEMORY} clients. */
  public McpSdkClientFactory(
      ObjectMapper objectMapper,
      JdkHttpClientProxyConfigurator proxyConfigurator,
      McpClientHeadersSupplierFactory headersSupplierFactory) {
    this(objectMapper, proxyConfigurator, headersSupplierFactory, null);
  }

  public McpSdkClientFactory(
      ObjectMapper objectMapper,
      JdkHttpClientProxyConfigurator proxyConfigurator,
      McpClientHeadersSupplierFactory headersSupplierFactory,
      @Nullable InMemoryMcpServerResolver inMemoryServerResolver) {
    this.objectMapper = objectMapper;
    this.proxyConfigurator = proxyConfigurator;
    this.headersSupplierFactory = headersSupplierFactory;
    this.inMemoryServerResolver = inMemoryServerResolver;
  }

  @Override
  public void validate(
      String clientId, McpClientConfigurationProperties.McpClientConfiguration config) {
    if (config.transport()
        instanceof
        McpClientConfigurationProperties.InMemoryMcpClientTransportConfiguration inMemory) {
      resolveInMemoryServer(clientId, inMemory);
    }
  }

  @Override
  public McpClientDelegate createClient(
      String clientId, McpClientConfigurationProperties.McpClientConfiguration config) {
    if (config.transport()
        instanceof
        McpClientConfigurationProperties.InMemoryMcpClientTransportConfiguration inMemory) {
      return createInMemoryClient(clientId, config, inMemory);
    }

    var clientBuilder =
        McpClient.sync(createTransport(config))
            .clientInfo(
                McpSchema.Implementation.builder("Camunda 8 MCP Connector", "1.0.0").build())
            .capabilities(McpSchema.ClientCapabilities.builder().roots(false).build());

    applyTimeouts(clientBuilder, config);

    return new McpSdkMcpClientDelegate(clientId, clientBuilder.build(), objectMapper);
  }

  private McpClientDelegate createInMemoryClient(
      String clientId,
      McpClientConfigurationProperties.McpClientConfiguration config,
      McpClientConfigurationProperties.InMemoryMcpClientTransportConfiguration inMemoryConfig) {
    final var serverDefinition = resolveInMemoryServer(clientId, inMemoryConfig);
    final var transport = new InMemoryMcpTransport();

    final var server =
        McpServer.sync(transport.serverTransportProvider())
            .serverInfo(serverDefinition.name(), serverDefinition.version())
            .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
            .tools(serverDefinition.tools())
            .build();

    try {
      final var clientBuilder =
          McpClient.sync(transport.clientTransport())
              .clientInfo(
                  McpSchema.Implementation.builder("Camunda 8 MCP Connector", "1.0.0").build())
              .capabilities(McpSchema.ClientCapabilities.builder().roots(false).build());
      applyTimeouts(clientBuilder, config);

      return new McpSdkMcpClientDelegate(
          clientId, clientBuilder.build(), objectMapper, server::close);
    } catch (RuntimeException e) {
      server.close();
      throw e;
    }
  }

  private InMemoryMcpServer resolveInMemoryServer(
      String clientId,
      McpClientConfigurationProperties.InMemoryMcpClientTransportConfiguration inMemoryConfig) {
    if (inMemoryServerResolver == null) {
      throw new IllegalStateException(
          "MCP client '%s' is of type IN_MEMORY, which is not supported by this client factory"
              .formatted(clientId));
    }
    return inMemoryServerResolver.resolve(clientId, inMemoryConfig);
  }

  private void applyTimeouts(
      McpClient.SyncSpec clientBuilder,
      McpClientConfigurationProperties.McpClientConfiguration config) {
    Optional.ofNullable(config.initializationTimeout())
        .ifPresent(clientBuilder::initializationTimeout);
    Optional.ofNullable(config.toolExecutionTimeout()).ifPresent(clientBuilder::requestTimeout);
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
      case McpClientConfigurationProperties.InMemoryMcpClientTransportConfiguration ignored ->
          throw new IllegalStateException("IN_MEMORY clients do not use a standalone transport");
    };
  }

  private StdioClientTransport createStdioTransport(
      McpClientConfigurationProperties.StdioMcpClientTransportConfiguration stdioConfig) {

    return new StdioClientTransport(
        ServerParameters.builder(stdioConfig.command())
            .args(stdioConfig.args())
            .env(stdioConfig.env())
            .build(),
        McpJsonDefaults.getMapper());
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
        .httpRequestCustomizer(
            (request, method, uri, protocolVersion, context) -> {
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
        .httpRequestCustomizer(
            (request, method, uri, protocolVersion, context) -> {
              var headers = headerSuppliers.get();
              headers.forEach(request::header);
            })
        .build();
  }

  private Duration timeout(Duration setTimeout) {
    return setTimeout != null ? setTimeout : DEFAULT_TIMEOUT;
  }
}
