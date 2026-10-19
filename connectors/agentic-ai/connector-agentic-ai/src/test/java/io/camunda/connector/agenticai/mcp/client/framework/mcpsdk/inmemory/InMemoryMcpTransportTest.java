/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.mcpsdk.inmemory;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCNotification;
import io.modelcontextprotocol.spec.McpTransportSessionClosedException;
import org.junit.jupiter.api.Test;

class InMemoryMcpTransportTest {

  private final InMemoryMcpTransport transport = new InMemoryMcpTransport();

  @Test
  void rejectsSecondConnect() {
    McpServer.sync(transport.serverTransportProvider()).serverInfo("test", "1.0.0").build();
    final var client = transport.clientTransport();
    client.connect(message -> message).block();

    assertThatThrownBy(() -> client.connect(message -> message).block())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("already connected");
  }

  @Test
  void rejectsMessagesAndConnectAfterClose() {
    McpServer.sync(transport.serverTransportProvider()).serverInfo("test", "1.0.0").build();
    final var client = transport.clientTransport();
    client.connect(message -> message).block();

    client.closeGracefully().block();

    assertThatThrownBy(
            () -> client.sendMessage(new JSONRPCNotification("notifications/test", null)).block())
        .isInstanceOf(McpTransportSessionClosedException.class);
    assertThatThrownBy(() -> client.connect(message -> message).block())
        .isInstanceOf(McpTransportSessionClosedException.class);
  }
}
