/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.mcpsdk.rpc;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import org.junit.jupiter.api.Test;

class McpSdkMcpClientDelegateTest {

  private final McpSyncClient client = mock(McpSyncClient.class);
  private final AutoCloseable onClose = mock(AutoCloseable.class);

  @Test
  void closesClientBeforeRunningCleanupCallback() throws Exception {
    new McpSdkMcpClientDelegate("id", client, new ObjectMapper(), onClose).close();

    final var order = inOrder(client, onClose);
    order.verify(client).close();
    order.verify(onClose).close();
  }

  @Test
  void runsCleanupCallbackEvenIfClientCloseFails() throws Exception {
    doThrow(new IllegalStateException("close failed")).when(client).close();
    final var delegate = new McpSdkMcpClientDelegate("id", client, new ObjectMapper(), onClose);

    assertThatThrownBy(delegate::close)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("close failed");

    org.mockito.Mockito.verify(onClose).close();
  }

  @Test
  void closesClientWithoutCleanupCallback() throws Exception {
    new McpSdkMcpClientDelegate("id", client, new ObjectMapper()).close();

    org.mockito.Mockito.verify(client).close();
  }
}
