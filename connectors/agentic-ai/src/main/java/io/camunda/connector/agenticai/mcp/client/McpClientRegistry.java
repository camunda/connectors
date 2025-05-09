/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client;

import dev.langchain4j.mcp.client.McpClient;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class McpClientRegistry implements AutoCloseable {
  private static final Logger LOGGER = LoggerFactory.getLogger(McpClientRegistry.class);

  private final Map<String, McpClient> clients = new LinkedHashMap<>();

  public void register(String name, McpClient client) {
    Objects.requireNonNull(name, "Name must not be null");
    Objects.requireNonNull(client, "Client must not be null");

    if (clients.containsKey(name)) {
      throw new IllegalArgumentException("MCP client '%s' already registered".formatted(name));
    }

    clients.put(name, client);
  }

  public Map<String, McpClient> getClients() {
    return Collections.unmodifiableMap(clients);
  }

  public McpClient getClient(String name) {
    final var client = clients.get(name);
    if (client == null) {
      throw new IllegalArgumentException("No MCP client registered with name '%s'".formatted(name));
    }

    return client;
  }

  @Override
  public void close() {
    for (var entry : clients.entrySet()) {
      LOGGER.debug("Closing MCP client '{}'", entry.getKey());

      try {
        entry.getValue().close();
      } catch (Exception e) {
        LOGGER.warn("Failed to close MCP client '%s'".formatted(entry.getKey()), e);
      }
    }
  }
}
