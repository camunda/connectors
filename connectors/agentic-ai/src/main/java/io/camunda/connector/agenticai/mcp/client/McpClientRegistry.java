/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class McpClientRegistry<C extends AutoCloseable> implements AutoCloseable {
  private static final Logger LOGGER = LoggerFactory.getLogger(McpClientRegistry.class);

  private final Map<String, C> clients = new LinkedHashMap<>();

  public void register(String id, C client) {
    Objects.requireNonNull(id, "ID must not be null");
    Objects.requireNonNull(client, "Client must not be null");

    if (clients.containsKey(id)) {
      throw new IllegalArgumentException(
          "MCP client with ID '%s' already registered".formatted(id));
    }

    clients.put(id, client);
  }

  public Map<String, C> getClients() {
    return Collections.unmodifiableMap(clients);
  }

  public C getClient(String id) {
    final var client = clients.get(id);
    if (client == null) {
      throw new IllegalArgumentException("No MCP client registered with ID '%s'".formatted(id));
    }

    return client;
  }

  @Override
  public void close() {
    for (var entry : clients.entrySet()) {
      LOGGER.debug("Closing MCP client with ID '{}'", entry.getKey());

      try {
        entry.getValue().close();
      } catch (Exception e) {
        LOGGER.warn("Failed to close MCP client with ID '%s'".formatted(entry.getKey()), e);
      }
    }
  }
}
