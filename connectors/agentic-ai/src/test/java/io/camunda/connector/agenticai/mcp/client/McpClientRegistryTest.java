/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class McpClientRegistryTest {

  private McpClientRegistry<MockAutoCloseableClient> registry;

  @BeforeEach
  void setUp() {
    registry = new McpClientRegistry<>();
  }

  @Test
  void registersClientWithValidId() {
    var client = mock(MockAutoCloseableClient.class);
    registry.register("test-client", client);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = "  ")
  void throwsException_whenIdIsNullOrBlank(String id) {
    var client = mock(MockAutoCloseableClient.class);

    assertThatThrownBy(() -> registry.register(id, client))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("ID must not be null or empty");
  }

  @Test
  void throwsException_whenClientIsNull() {
    assertThatThrownBy(() -> registry.register("test-client", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("MCP client must not be null");
  }

  @Test
  void throwsException_whenRegisteringDuplicateId() {
    var client1 = mock(MockAutoCloseableClient.class);
    var client2 = mock(MockAutoCloseableClient.class);
    registry.register("test-client", client1);

    assertThatThrownBy(() -> registry.register("test-client", client2))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("MCP client with ID 'test-client' is already registered");
  }

  @Test
  void returnsClient_whenClientExists() {
    var client = mock(MockAutoCloseableClient.class);
    registry.register("test-client", client);

    var result = registry.getClient("test-client");

    assertThat(result).isEqualTo(client);
  }

  @Test
  void allowsMultipleClientsWithDifferentIds() {
    var client1 = mock(MockAutoCloseableClient.class);
    var client2 = mock(MockAutoCloseableClient.class);

    registry.register("client-1", client1);
    registry.register("client-2", client2);

    assertThat(registry.getClient("client-1")).isEqualTo(client1);
    assertThat(registry.getClient("client-2")).isEqualTo(client2);
  }

  @Test
  void throwsException_whenClientNotFound() {
    assertThatThrownBy(() -> registry.getClient("non-existent"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("No MCP client registered with ID 'non-existent'");
  }

  @Test
  void closesAllClients_whenRegistryIsClosed() throws Exception {
    var client1 = mock(MockAutoCloseableClient.class);
    var client2 = mock(MockAutoCloseableClient.class);
    registry.register("client-1", client1);
    registry.register("client-2", client2);

    registry.close();

    verify(client1).close();
    verify(client2).close();
  }

  @Test
  void continuesClosingOtherClients_whenOneClientThrowsException() throws Exception {
    var client1 = mock(MockAutoCloseableClient.class);
    var client2 = mock(MockAutoCloseableClient.class);
    var client3 = mock(MockAutoCloseableClient.class);
    doThrow(new RuntimeException("Close failed")).when(client2).close();
    registry.register("client-1", client1);
    registry.register("client-2", client2);
    registry.register("client-3", client3);

    registry.close();

    verify(client1).close();
    verify(client2).close();
    verify(client3).close();
  }

  @Test
  void doesNotThrowException_whenClosingEmptyRegistry() {
    assertThat(registry)
        .satisfies(
            r -> {
              try {
                r.close();
              } catch (Exception e) {
                throw new RuntimeException("Should not throw exception", e);
              }
            });
  }

  @Test
  void doesNotThrowException_whenClientCloseThrowsException() throws Exception {
    var client = mock(MockAutoCloseableClient.class);
    doThrow(new RuntimeException("Close failed")).when(client).close();
    registry.register("test-client", client);

    assertThat(registry)
        .satisfies(
            r -> {
              try {
                r.close();
              } catch (Exception e) {
                throw new RuntimeException("Should not throw exception", e);
              }
            });
  }

  interface MockAutoCloseableClient extends AutoCloseable {}
}
