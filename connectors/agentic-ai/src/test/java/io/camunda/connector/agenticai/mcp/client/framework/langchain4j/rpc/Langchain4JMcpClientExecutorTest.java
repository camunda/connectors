/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.langchain4j.rpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.mcp.client.McpClient;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool.ToolSpecificationConverter;
import io.camunda.connector.agenticai.mcp.client.filters.FilterOptions;
import io.camunda.connector.agenticai.mcp.client.filters.FilterOptionsBuilder;
import io.camunda.connector.agenticai.mcp.client.model.McpClientOperation;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientCallToolResult;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientListResourcesResult;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientListToolsResult;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class Langchain4JMcpClientExecutorTest {

  private static final FilterOptions EMPTY_FILTER = FilterOptionsBuilder.builder().build();

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Mock private ToolSpecificationConverter toolSpecificationConverter;
  @Mock private McpClient mcpClient;

  private Langchain4JMcpClientExecutor executor;

  @BeforeEach
  void setUp() {
    executor = new Langchain4JMcpClientExecutor(objectMapper, toolSpecificationConverter);
    when(mcpClient.key()).thenReturn("test-client");
  }

  @Test
  void returnsMcpListToolsResult_whenListToolsExecuted() {
    final var operation = McpClientOperation.withoutParams("tools/list");
    final var result = executor.execute(mcpClient, operation, EMPTY_FILTER);

    assertThat(result).isInstanceOf(McpClientListToolsResult.class);
  }

  @Test
  void returnsMcpCallToolResult_whenCallToolsExecuted() {
    final var operation =
        McpClientOperation.withParams(
            "tools/call", Map.of("name", "test-tool", "arguments", Map.of("arg1", "value1")));
    final var result = executor.execute(mcpClient, operation, EMPTY_FILTER);

    assertThat(result).isInstanceOf(McpClientCallToolResult.class);
  }

  @Test
  void returnsMcpListResourcesResult_whenListResourcesExecuted() {
    final var operation = McpClientOperation.withoutParams("resources/list");
    final var result = executor.execute(mcpClient, operation, EMPTY_FILTER);

    assertThat(result).isInstanceOf(McpClientListResourcesResult.class);
  }
}
