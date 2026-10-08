/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.mcpsdk.inmemory;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import java.util.List;

/**
 * SPI for exposing Java code living in the connector runtime as an MCP server to an MCP client of
 * type {@code IN_MEMORY}. Implementations must be Spring beans and are referenced from the client
 * configuration by bean name or by class name.
 *
 * <p>This SPI is tied to the MCP SDK ({@code mcpsdk}) client framework.
 */
public interface InMemoryMcpServer {

  /** Name reported by the server during MCP initialization. */
  default String name() {
    return getClass().getSimpleName();
  }

  /** Version reported by the server during MCP initialization. */
  default String version() {
    return "1.0.0";
  }

  /** Tools offered through {@code tools/list} and {@code tools/call}. */
  List<SyncToolSpecification> tools();
}
