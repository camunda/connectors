/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Scheduler;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.AuthenticationConfiguration;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.AuthenticationConfiguration.AuthenticationType;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.McpClientConfiguration;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.McpClientConfiguration.McpClientType;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.SseHttpMcpClientTransportConfiguration;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.StreamableHttpMcpClientTransportConfiguration;
import io.camunda.connector.agenticai.mcp.client.configuration.McpRemoteClientConfigurationProperties.ClientConfiguration;
import io.camunda.connector.agenticai.mcp.client.configuration.McpRemoteClientConfigurationProperties.ClientConfiguration.ClientCacheConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpRemoteClientTransportConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpRemoteClientTransportConfiguration.SseHttpMcpRemoteClientTransportConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpRemoteClientTransportConfiguration.StreamableHttpMcpRemoteClientTransportConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.auth.Authentication;
import io.camunda.connector.agenticai.mcp.client.model.auth.BasicAuthentication;
import io.camunda.connector.agenticai.mcp.client.model.auth.BearerAuthentication;
import io.camunda.connector.agenticai.mcp.client.model.auth.NoAuthentication;
import io.camunda.connector.agenticai.mcp.client.model.auth.OAuthAuthentication;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class McpRemoteClientRegistry<C extends AutoCloseable> implements AutoCloseable {
  private static final Logger LOGGER = LoggerFactory.getLogger(McpRemoteClientRegistry.class);

  private final Cache<@NonNull McpRemoteClientIdentifier, C> cache;
  private final McpClientFactory<C> clientFactory;

  public McpRemoteClientRegistry(
      ClientConfiguration clientConfig, McpClientFactory<C> clientFactory) {
    this.cache = createCache(clientConfig.cache());
    this.clientFactory = clientFactory;
  }

  private Cache<@NonNull McpRemoteClientIdentifier, C> createCache(
      ClientCacheConfiguration cacheConfig) {
    final var maximumSize = cacheConfig.enabled() ? cacheConfig.maximumSize() : 0;

    return Caffeine.newBuilder()
        .scheduler(Scheduler.systemScheduler())
        .maximumSize(maximumSize)
        .expireAfterAccess(cacheConfig.expireAfter())
        .evictionListener(
            (key, value, cause) -> {
              LOGGER.info(
                  "MCP({}): Removing cached remote HTTP client (removal cause: {})", key, cause);
              this.closeClient(key, value);
            })
        .build();
  }

  /**
   * Gets or creates an MCP client for the given identifier and transport configuration.
   * 
   * <p><strong>Important:</strong> When {@code cacheable=false}, the caller is responsible
   * for closing the returned client to prevent resource leaks. Non-cached clients are NOT
   * tracked by the registry and will NOT be automatically closed when the registry is closed.
   * 
   * <p>Use {@code cacheable=false} when authentication credentials are process-specific and
   * should not be shared across invocations.
   *
   * @param clientId The unique identifier for the client
   * @param transport The transport configuration
   * @param cacheable If true, the client will be cached and reused; if false, a new client
   *                  is created each time and the caller must close it
   * @return The MCP client instance
   */
  public C getClient(
      McpRemoteClientIdentifier clientId,
      McpRemoteClientTransportConfiguration transport,
      boolean cacheable) {
    if (cacheable) {
      return this.cache.get(clientId, key -> this.createClient(clientId, transport));
    } else {
      LOGGER.info("MCP({}): Creating non-cached remote HTTP client - caller responsible for closing", clientId);
      return this.createClient(clientId, transport);
    }
  }

  private C createClient(
      McpRemoteClientIdentifier clientId, McpRemoteClientTransportConfiguration transport) {
    LOGGER.info("MCP({}): Creating remote HTTP client", clientId);
    return this.clientFactory.createClient(
        clientId.toString(), createClientConfiguration(transport));
  }

  private McpClientConfiguration createClientConfiguration(
      McpRemoteClientTransportConfiguration transport) {
    final var builder = McpClientConfiguration.builder().enabled(true);

    switch (transport) {
      case StreamableHttpMcpRemoteClientTransportConfiguration httpConfig -> {
        final var httpConnectionConfig = httpConfig.http();
        builder.type(McpClientType.HTTP);
        builder.http(
            new StreamableHttpMcpClientTransportConfiguration(
                httpConnectionConfig.url(),
                httpConnectionConfig.headers(),
                resolveAuthentication(httpConnectionConfig.authentication()),
                httpConnectionConfig.timeout()));
      }

      case SseHttpMcpRemoteClientTransportConfiguration sseConfig -> {
        final var sseConnectionConfig = sseConfig.sse();
        builder.type(McpClientType.SSE);
        builder.sse(
            new SseHttpMcpClientTransportConfiguration(
                sseConnectionConfig.url(),
                sseConnectionConfig.headers(),
                resolveAuthentication(sseConnectionConfig.authentication()),
                sseConnectionConfig.timeout()));
      }
    }

    return builder.build();
  }

  private AuthenticationConfiguration resolveAuthentication(Authentication authentication) {
    if (authentication == null) {
      return AuthenticationConfiguration.builder().type(AuthenticationType.NONE).build();
    }

    final var builder = AuthenticationConfiguration.builder();
    switch (authentication) {
      case NoAuthentication ignored -> builder.type(AuthenticationType.NONE);
      case BasicAuthentication basicAuthentication ->
          builder.type(AuthenticationType.BASIC).basic(basicAuthentication);
      case BearerAuthentication bearerAuthentication ->
          builder.type(AuthenticationType.BEARER).bearer(bearerAuthentication);
      case OAuthAuthentication oAuthAuthentication ->
          builder.type(AuthenticationType.OAUTH).oauth(oAuthAuthentication);
    }

    return builder.build();
  }

  @Override
  public void close() {
    this.cache.asMap().forEach(this::closeClient);
  }

  public void closeClient(Object clientId, Object client) {
    LOGGER.info("MCP({}): Closing remote HTTP client", clientId);
    if (client instanceof AutoCloseable autoCloseable) {
      try {
        autoCloseable.close();
      } catch (Exception e) {
        LOGGER.error("MCP({}): Error closing remote HTTP client", clientId, e);
      }
    }
  }

  public record McpRemoteClientIdentifier(Long processDefinitionKey, String elementId) {
    public McpRemoteClientIdentifier {
      if (processDefinitionKey == null || processDefinitionKey <= 0) {
        throw new IllegalArgumentException("Process definition key must not be null or negative");
      }

      if (elementId == null || elementId.isBlank()) {
        throw new IllegalArgumentException("elementId cannot be null or empty");
      }
    }

    @Override
    public String toString() {
      return "%s_%s".formatted(processDefinitionKey, elementId);
    }

    public static McpRemoteClientIdentifier from(OutboundConnectorContext context) {
      return new McpRemoteClientIdentifier(
          context.getJobContext().getProcessDefinitionKey(),
          context.getJobContext().getElementId());
    }
  }
}
