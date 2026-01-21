/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.camunda.connector.agenticai.mcp.client.execution.McpClientDelegate;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class McpClientRegistryTest {

  private McpClientRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new McpClientRegistry();
  }

  @Test
  void registersClientWithValidId() {
    var client = mock(McpClientDelegate.class);
    registry.register("test-client", () -> client);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = "  ")
  void throwsException_whenRegisterIdIsNullOrBlank(String id) {
    var client = mock(McpClientDelegate.class);

    assertThatThrownBy(() -> registry.register(id, () -> client))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("MCP client ID must not be null or empty");
  }

  @Test
  void throwsException_whenClientSupplierIsNull() {
    assertThatThrownBy(() -> registry.register("test-client", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("MCP client supplier must not be null");
  }

  @Test
  void throwsException_whenRegisteringDuplicateId() {
    var client1 = mock(McpClientDelegate.class);
    var client2 = mock(McpClientDelegate.class);
    registry.register("test-client", () -> client1);

    assertThatThrownBy(() -> registry.register("test-client", () -> client2))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("MCP client with ID 'test-client' is already registered");
  }

  @Test
  void returnsClient_whenClientExists() {
    var client = mock(McpClientDelegate.class);
    registry.register("test-client", () -> client);

    var result = registry.getClient("test-client");

    assertThat(result).isEqualTo(client);
  }

  @Test
  void throwsException_whenClientSupplierReturnsNull() {
    registry.register("test-client", () -> null);

    assertThatThrownBy(() -> registry.getClient("test-client"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("MCP client supplier for ID 'test-client' returned null");
  }

  @Test
  void allowsMultipleClientsWithDifferentIds() {
    var client1 = mock(McpClientDelegate.class);
    var client2 = mock(McpClientDelegate.class);
    var client3 = mock(McpClientDelegate.class);

    registry.register("client-1", () -> client1);
    registry.register("client-2", () -> client2);
    registry.register("client-3", () -> client3);

    assertThat(registry.getClient("client-1")).isEqualTo(client1);
    assertThat(registry.getClient("client-2")).isEqualTo(client2);
    assertThat(registry.getClient("client-3")).isEqualTo(client3);
  }

  @Test
  void throwsException_whenClientNotFound() {
    assertThatThrownBy(() -> registry.getClient("non-existent"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("No MCP client registered with ID 'non-existent'");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = "  ")
  void throwsException_whenGetClientIdIsNullOrBlank(String id) {
    assertThatThrownBy(() -> registry.getClient(id))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("MCP client ID must not be null or empty");
  }

  @Test
  void closesAllConstructedClients_whenRegistryIsClosed() throws Exception {
    var client1 = mock(McpClientDelegate.class);
    var client2 = mock(McpClientDelegate.class);
    var client3 = mock(McpClientDelegate.class);

    registry.register("client-1", () -> client1);
    registerAndForceConstruction("client-2", () -> client2);
    registry.register("client-3", () -> client3);

    registry.close();

    verify(client1, never()).close();
    verify(client2).close();
    verify(client3, never()).close();
  }

  @Test
  void doesNotCloseNotConstructedClient_whenRegistryIsClosed() throws Exception {
    var client1 = mock(McpClientDelegate.class);
    var client2 = mock(McpClientDelegate.class);
    var client3 = mock(McpClientDelegate.class);

    registry.register("client-1", () -> client1);
    registry.register("client-2", () -> client2);
    registry.register("client-3", () -> client3);

    registry.close();

    verify(client1, never()).close();
    verify(client2, never()).close();
    verify(client3, never()).close();
  }

  @Test
  void continuesClosingOtherClients_whenOneClientThrowsException() throws Exception {
    var client1 = mock(McpClientDelegate.class);
    var client2 = mock(McpClientDelegate.class);
    var client3 = mock(McpClientDelegate.class);
    doThrow(new RuntimeException("Close failed")).when(client2).close();

    // force all clients to be constructed
    registerAndForceConstruction("client-1", () -> client1);
    registerAndForceConstruction("client-2", () -> client2);
    registerAndForceConstruction("client-3", () -> client3);

    registry.close();

    verify(client1).close();
    verify(client2).close();
    verify(client3).close();
  }

  @Test
  void doesNotThrowException_whenClosingEmptyRegistry() {
    assertThatCode(() -> registry.close()).doesNotThrowAnyException();
  }

  @Test
  void doesNotThrowException_whenClientCloseThrowsException() throws Exception {
    var client = mock(McpClientDelegate.class);
    doThrow(new RuntimeException("Close failed")).when(client).close();
    registerAndForceConstruction("test-client", () -> client);

    assertThatCode(() -> registry.close()).doesNotThrowAnyException();
  }

  private void registerAndForceConstruction(
      String id, Supplier<McpClientDelegate> clientSupplier) {
    registry.register(id, clientSupplier);
    registry.getClient(id);
  }
}
