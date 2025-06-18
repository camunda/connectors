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
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.McpClientConfiguration;
import io.camunda.connector.agenticai.mcp.client.configuration.McpRemoteClientConfigurationProperties.ClientConfiguration;
import io.camunda.connector.agenticai.mcp.client.configuration.McpRemoteClientConfigurationProperties.ClientConfiguration.ClientCacheConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpRemoteClientRequest.McpRemoteClientRequestData.HttpConnectionConfiguration;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class McpRemoteClientRegistry<C extends AutoCloseable> implements AutoCloseable {
  private static final Logger LOGGER = LoggerFactory.getLogger(McpRemoteClientRegistry.class);

  private final ClientConfiguration clientConfig;
  private final Cache<McpRemoteClientIdentifier, C> cache;
  private final McpClientFactory<C> clientFactory;

  public McpRemoteClientRegistry(
      ClientConfiguration clientConfig, McpClientFactory<C> clientFactory) {
    this.clientConfig = clientConfig;
    this.cache = createCache(clientConfig.cache());
    this.clientFactory = clientFactory;
  }

  private Cache<McpRemoteClientIdentifier, C> createCache(ClientCacheConfiguration cacheConfig) {
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

  public C getClient(McpRemoteClientIdentifier clientId, HttpConnectionConfiguration connection) {
    return this.cache.get(
        clientId,
        key -> {
          LOGGER.info("MCP({}): Creating remote HTTP client", clientId);
          return this.clientFactory.createClient(
              clientId.toString(), createClientConfiguration(connection));
        });
  }

  private McpClientConfiguration createClientConfiguration(HttpConnectionConfiguration connection) {
    final var httpTransportConfiguration =
        new McpClientConfigurationProperties.HttpMcpClientTransportConfiguration(
            connection.sseUrl(),
            connection.headers(),
            connection.timeout(),
            clientConfig.logRequests(),
            clientConfig.logResponses());

    return new McpClientConfiguration(true, null, httpTransportConfiguration, null, null, null);
  }

  @Override
  public void close() {
    this.cache.asMap().forEach(this::closeClient);
  }

  private void closeClient(Object clientId, Object client) {
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
