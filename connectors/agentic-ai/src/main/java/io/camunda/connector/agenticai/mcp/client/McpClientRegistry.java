/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class McpClientRegistry<C extends AutoCloseable> implements AutoCloseable {
  private static final Logger LOGGER = LoggerFactory.getLogger(McpClientRegistry.class);

  private final Map<String, C> clients = new LinkedHashMap<>();
  private final Map<String, Supplier<C>> clientSuppliers = new LinkedHashMap<>();

  public void register(String id, Supplier<C> clientSupplier) {
    validateClientId(id);

    if (clientSuppliers.containsKey(id)) {
      throw new IllegalArgumentException(
          "MCP client with ID '%s' is already registered".formatted(id));
    }

    if (clientSupplier == null) {
      throw new IllegalArgumentException("MCP client supplier must not be null");
    }

    clientSuppliers.put(id, clientSupplier);
  }

  public C getClient(String id) {
    validateClientId(id);

    return clients.computeIfAbsent(
        id,
        clientId -> {
          final var clientSupplier = clientSuppliers.get(clientId);
          if (clientSupplier == null) {
            throw new IllegalArgumentException(
                "No MCP client registered with ID '%s'".formatted(clientId));
          }

          final var client = clientSupplier.get();
          if (client == null) {
            throw new IllegalArgumentException(
                "MCP client supplier for ID '%s' returned null".formatted(clientId));
          }

          return client;
        });
  }

  private void validateClientId(String id) {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("MCP client ID must not be null or empty");
    }
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
