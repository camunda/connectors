/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.langchain4j;

import static io.camunda.connector.agenticai.model.message.content.TextContent.textContent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.tool.ToolExecutionResult;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool.ToolSpecificationConverter;
import io.camunda.connector.agenticai.mcp.client.McpToolNameFilter;
import io.camunda.connector.agenticai.mcp.client.model.McpClientOperation.McpClientCallToolOperation;
import io.camunda.connector.agenticai.mcp.client.model.McpClientOperation.McpClientListToolsOperation;
import io.camunda.connector.agenticai.mcp.client.model.McpClientToolsConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientCallToolResult;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientListToolsResult;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class Langchain4JMcpClientExecutorTest {

  private static final McpToolNameFilter EMPTY_FILTER =
      McpToolNameFilter.from(new McpClientToolsConfiguration(List.of(), List.of()));

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Mock private ToolSpecificationConverter toolSpecificationConverter;
  @Mock private McpClient mcpClient;

  private Langchain4JMcpClientExecutor executor;

  @BeforeEach
  void setUp() {
    executor = new Langchain4JMcpClientExecutor(objectMapper, toolSpecificationConverter);
    when(mcpClient.key()).thenReturn("test-client");
  }

  @Nested
  class ListTools {

    @Test
    void returnsEmptyList_whenNoToolsAvailable() {
      when(mcpClient.listTools()).thenReturn(Collections.emptyList());

      final var result =
          executor.execute(mcpClient, new McpClientListToolsOperation(), EMPTY_FILTER);

      assertThat(result)
          .isInstanceOfSatisfying(
              McpClientListToolsResult.class, res -> assertThat(res.toolDefinitions()).isEmpty());
    }

    @Test
    void returnsToolDefinitions_whenToolsAvailable() {
      final var toolSpec1 = createToolSpecification("tool1", "Tool 1 description");
      final var toolSpec2 = createToolSpecification("tool2", "Tool 2 description");
      final var toolDefinition1 = createToolDefinition("tool1", "Tool 1 description");
      final var toolDefinition2 = createToolDefinition("tool2", "Tool 2 description");

      when(mcpClient.listTools()).thenReturn(List.of(toolSpec1, toolSpec2));

      when(toolSpecificationConverter.asToolDefinition(toolSpec1)).thenReturn(toolDefinition1);
      when(toolSpecificationConverter.asToolDefinition(toolSpec2)).thenReturn(toolDefinition2);

      final var result =
          executor.execute(mcpClient, new McpClientListToolsOperation(), EMPTY_FILTER);

      assertThat(result)
          .isInstanceOfSatisfying(
              McpClientListToolsResult.class,
              res ->
                  assertThat(res.toolDefinitions())
                      .containsExactly(toolDefinition1, toolDefinition2));
    }

    @Test
    void filtersTools_whenFilterConfigured() {
      final var toolSpec1 = createToolSpecification("allowed-tool", "Allowed tool");
      final var toolSpec2 = createToolSpecification("blocked-tool", "Blocked tool");
      final var toolDefinition1 = createToolDefinition("allowed-tool", "Allowed tool");
      final var filter =
          McpToolNameFilter.from(
              new McpClientToolsConfiguration(List.of("allowed-tool"), List.of()));

      when(mcpClient.listTools()).thenReturn(List.of(toolSpec1, toolSpec2));
      when(toolSpecificationConverter.asToolDefinition(toolSpec1)).thenReturn(toolDefinition1);

      final var result = executor.execute(mcpClient, new McpClientListToolsOperation(), filter);

      assertThat(result)
          .isInstanceOfSatisfying(
              McpClientListToolsResult.class,
              res -> {
                assertThat(res.toolDefinitions()).containsExactly(toolDefinition1);
              });
    }

    @Test
    void returnsEmptyList_whenAllToolsFiltered() {
      final var toolSpec1 = createToolSpecification("blocked-tool1", "Blocked tool 1");
      final var toolSpec2 = createToolSpecification("blocked-tool2", "Blocked tool 2");
      final var filter =
          McpToolNameFilter.from(
              new McpClientToolsConfiguration(List.of("allowed-tool"), List.of()));

      when(mcpClient.listTools()).thenReturn(List.of(toolSpec1, toolSpec2));

      final var result = executor.execute(mcpClient, new McpClientListToolsOperation(), filter);

      assertThat(result)
          .isInstanceOfSatisfying(
              McpClientListToolsResult.class, res -> assertThat(res.toolDefinitions()).isEmpty());
    }
  }

  @Nested
  class CallTool {

    @Test
    void executesTool_whenToolAllowedByFilter() {
      final var operation =
          McpClientCallToolOperation.create("test-tool", Map.of("arg1", "value1"));

      when(mcpClient.executeTool(any())).thenReturn(toolExecutionResult("Tool execution result"));

      final var result = executor.execute(mcpClient, operation, EMPTY_FILTER);

      assertThat(result)
          .isInstanceOfSatisfying(
              McpClientCallToolResult.class,
              res -> {
                assertThat(res.name()).isEqualTo("test-tool");
                assertThat(res.isError()).isFalse();
                assertThat(res.content()).containsExactly(textContent("Tool execution result"));
              });
    }

    @ParameterizedTest
    @NullAndEmptySource
    void handlesEmptyArguments(Map<String, Object> arguments) {
      final var operation = McpClientCallToolOperation.create("test-tool", arguments);

      when(mcpClient.executeTool(any())).thenReturn(toolExecutionResult("Success with no args"));

      final var result = executor.execute(mcpClient, operation, EMPTY_FILTER);

      assertThat(result)
          .isInstanceOfSatisfying(
              McpClientCallToolResult.class,
              res -> {
                assertThat(res.name()).isEqualTo("test-tool");
                assertThat(res.isError()).isFalse();
                assertThat(res.content()).containsExactly(textContent("Success with no args"));
              });
    }

    @ParameterizedTest
    @MethodSource("toolExecutionArguments")
    void handlesDifferentTypesOfArguments(String toolName, Map<String, Object> arguments) {
      final var operation = McpClientCallToolOperation.create(toolName, arguments);

      when(mcpClient.executeTool(any())).thenReturn(toolExecutionResult("Successful result"));

      final var result = executor.execute(mcpClient, operation, EMPTY_FILTER);

      assertThat(result)
          .isInstanceOfSatisfying(
              McpClientCallToolResult.class,
              res -> {
                assertThat(res.name()).isEqualTo(toolName);
                assertThat(res.isError()).isFalse();
                assertThat(res.content()).containsExactly(textContent("Successful result"));
              });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void returnsDefaultResponseText_whenResponseIsBlank(String resultText) {
      final var operation =
          McpClientCallToolOperation.create("test-tool", Map.of("arg1", "value1"));

      when(mcpClient.executeTool(any())).thenReturn(toolExecutionResult(resultText));

      final var result = executor.execute(mcpClient, operation, EMPTY_FILTER);

      assertThat(result)
          .isInstanceOfSatisfying(
              McpClientCallToolResult.class,
              res -> {
                assertThat(res.name()).isEqualTo("test-tool");
                assertThat(res.isError()).isFalse();
                assertThat(res.content())
                    .containsExactly(
                        textContent("Tool execution succeeded, but returned no result."));
              });
    }

    @Test
    void throwsException_whenToolNameIsNull() {
      reset(mcpClient);
      final var operation = McpClientCallToolOperation.create(null, Map.of("arg1", "value1"));

      assertThatThrownBy(() -> executor.execute(mcpClient, operation, EMPTY_FILTER))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Tool name must not be null");
    }

    @Test
    void returnsError_whenToolNotIncludedInFilter() {
      final var operation =
          McpClientCallToolOperation.create("blocked-tool", Map.of("arg1", "value1"));
      final var filter =
          McpToolNameFilter.from(
              new McpClientToolsConfiguration(List.of("allowed-tool"), List.of()));

      final var result = executor.execute(mcpClient, operation, filter);

      assertThat(result)
          .isInstanceOfSatisfying(
              McpClientCallToolResult.class,
              res -> {
                assertThat(res.name()).isEqualTo("blocked-tool");
                assertThat(res.isError()).isTrue();
                assertThat(res.content())
                    .containsExactly(
                        textContent(
                            "Executing tool 'blocked-tool' is not allowed by filter configuration: [included=[allowed-tool], excluded=[]]"));
              });
    }

    @Test
    void returnsError_whenToolExcludedInFilter() {
      final var operation =
          McpClientCallToolOperation.create("blocked-tool", Map.of("arg1", "value1"));
      final var filter =
          McpToolNameFilter.from(
              new McpClientToolsConfiguration(List.of(), List.of("blocked-tool")));

      final var result = executor.execute(mcpClient, operation, filter);

      assertThat(result)
          .isInstanceOfSatisfying(
              McpClientCallToolResult.class,
              res -> {
                assertThat(res.name()).isEqualTo("blocked-tool");
                assertThat(res.isError()).isTrue();
                assertThat(res.content())
                    .containsExactly(
                        textContent(
                            "Executing tool 'blocked-tool' is not allowed by filter configuration: [included=[], excluded=[blocked-tool]]"));
              });
    }

    @Test
    void returnsError_whenToolExecutionFails() {
      final var operation =
          McpClientCallToolOperation.create("failing-tool", Map.of("arg1", "value1"));

      when(mcpClient.executeTool(any())).thenThrow(new RuntimeException("Tool execution failed"));

      final var result = executor.execute(mcpClient, operation, EMPTY_FILTER);

      assertThat(result)
          .isInstanceOfSatisfying(
              McpClientCallToolResult.class,
              res -> {
                assertThat(res.name()).isEqualTo("failing-tool");
                assertThat(res.isError()).isTrue();
                assertThat(res.content())
                    .containsExactly(
                        textContent("Error executing tool 'failing-tool': Tool execution failed"));
              });
    }

    static Stream<Arguments> toolExecutionArguments() {
      return Stream.of(
          arguments("valid-tool", Map.of("key", "value")),
          arguments("tool-with-complex-args", Map.of("nested", Map.of("key", "value"))));
    }
  }

  private ToolExecutionResult toolExecutionResult(String resultText) {
    return ToolExecutionResult.builder().resultText(resultText).build();
  }

  private ToolSpecification createToolSpecification(String name, String description) {
    return ToolSpecification.builder()
        .name(name)
        .description(description)
        .parameters(JsonObjectSchema.builder().build())
        .build();
  }

  private ToolDefinition createToolDefinition(String name, String description) {
    return ToolDefinition.builder()
        .name(name)
        .description(description)
        .inputSchema(Map.of("type", "object"))
        .build();
  }
}
