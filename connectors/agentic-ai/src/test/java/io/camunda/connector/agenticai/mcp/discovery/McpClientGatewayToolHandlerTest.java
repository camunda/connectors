/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.discovery;

import static io.camunda.connector.agenticai.mcp.discovery.McpClientGatewayToolHandler.PROPERTY_MCP_CLIENTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.mcp.client.model.McpToolDefinition;
import io.camunda.connector.agenticai.mcp.client.model.McpToolDefinitionBuilder;
import io.camunda.connector.agenticai.mcp.client.model.content.McpTextContent;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientCallToolResult;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientListToolsResult;
import io.camunda.connector.agenticai.model.tool.GatewayToolDefinition;
import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.api.error.ConnectorException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class McpClientGatewayToolHandlerTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private McpClientGatewayToolHandler handler;

  @BeforeEach
  void setUp() {
    handler = new McpClientGatewayToolHandler(objectMapper);
  }

  @Nested
  class TypeIdentification {

    @Test
    void returnsCorrectType() {
      assertThat(handler.type()).isEqualTo("mcpClient");
    }
  }

  @Nested
  class ToolDiscoveryInitiation {

    @Test
    void returnsEmptyResult_whenNoMcpGatewayToolDefinitions() {
      var agentContext = AgentContext.empty();
      var gatewayToolDefinitions =
          List.of(
              createGatewayToolDefinition("other", "tool1"),
              createGatewayToolDefinition("different", "tool2"));

      var result = handler.initiateToolDiscovery(agentContext, gatewayToolDefinitions);

      assertThat(result.agentContext()).isEqualTo(agentContext);
      assertThat(result.toolDiscoveryToolCalls()).isEmpty();
    }

    @Test
    void createsDiscoveryToolCalls_whenMcpGatewayToolDefinitionsPresent() {
      var agentContext = AgentContext.empty();
      var gatewayToolDefinitions =
          List.of(
              createGatewayToolDefinition("mcpClient", "mcp1"),
              createGatewayToolDefinition("mcpClient", "mcp2"));

      var result = handler.initiateToolDiscovery(agentContext, gatewayToolDefinitions);

      assertThat(result.agentContext().properties())
          .containsEntry(PROPERTY_MCP_CLIENTS, List.of("mcp1", "mcp2"));
      assertThat(result.toolDiscoveryToolCalls()).hasSize(2);
      assertThat(result.toolDiscoveryToolCalls())
          .satisfiesExactly(
              toolCall -> {
                assertThat(toolCall.id()).isEqualTo("MCP_toolsList_mcp1");
                assertThat(toolCall.name()).isEqualTo("mcp1");
                assertThat(toolCall.arguments()).containsExactly(Map.entry("method", "tools/list"));
              },
              toolCall -> {
                assertThat(toolCall.id()).isEqualTo("MCP_toolsList_mcp2");
                assertThat(toolCall.name()).isEqualTo("mcp2");
                assertThat(toolCall.arguments()).containsExactly(Map.entry("method", "tools/list"));
              });
    }

    @ParameterizedTest
    @ValueSource(strings = {"mcp___client", "mcp_____client", "name___", "___name"})
    void throwsException_whenMcpGatewayToolDefinitionNameContainsSeparator(String invalidName) {
      var agentContext = AgentContext.empty();
      var gatewayToolDefinitions = List.of(createGatewayToolDefinition("mcpClient", invalidName));

      assertThatThrownBy(() -> handler.initiateToolDiscovery(agentContext, gatewayToolDefinitions))
          .isInstanceOf(ConnectorException.class)
          .asInstanceOf(InstanceOfAssertFactories.type(ConnectorException.class))
          .satisfies(
              e -> {
                assertThat(e.getErrorCode()).isEqualTo("MCP_GATEWAY_INVALID_TOOL_DEFINITIONS");
                assertThat(e.getMessage())
                    .isEqualTo(
                        "Invalid MCP client activity ID(s) detected: ['%s']. Activity IDs must not contain the reserved separator '___'. Please rename the affected activities in the BPMN model."
                            .formatted(invalidName));
              });
    }

    @Test
    void throwsException_listingAllInvalidNames() {
      var agentContext = AgentContext.empty();
      var gatewayToolDefinitions =
          List.of(
              createGatewayToolDefinition("mcpClient", "mcp___1"),
              createGatewayToolDefinition("mcpClient", "valid"),
              createGatewayToolDefinition("mcpClient", "mcp___2"));

      assertThatThrownBy(() -> handler.initiateToolDiscovery(agentContext, gatewayToolDefinitions))
          .isInstanceOf(ConnectorException.class)
          .asInstanceOf(InstanceOfAssertFactories.type(ConnectorException.class))
          .satisfies(
              e -> {
                assertThat(e.getErrorCode()).isEqualTo("MCP_GATEWAY_INVALID_TOOL_DEFINITIONS");
                assertThat(e.getMessage())
                    .isEqualTo(
                        "Invalid MCP client activity ID(s) detected: ['mcp___1', 'mcp___2']. Activity IDs must not contain the reserved separator '___'. Please rename the affected activities in the BPMN model.");
              });
    }

    @Test
    void filtersMcpGatewayTools_whenMixedGatewayToolDefinitions() {
      var agentContext = AgentContext.empty();
      var gatewayToolDefinitions =
          List.of(
              createGatewayToolDefinition("mcpClient", "mcp1"),
              createGatewayToolDefinition("other", "other1"),
              createGatewayToolDefinition("mcpClient", "mcp2"));

      var result = handler.initiateToolDiscovery(agentContext, gatewayToolDefinitions);

      assertThat(result.agentContext().properties())
          .containsEntry(PROPERTY_MCP_CLIENTS, List.of("mcp1", "mcp2"));
      assertThat(result.toolDiscoveryToolCalls()).hasSize(2);
    }
  }

  @Nested
  class AllToolDiscoveryResultsPresent {

    @Test
    void returnsTrue_whenAllMcpClientToolDiscoveryResultsPresent() {
      var agentContext =
          AgentContext.empty().withProperty(PROPERTY_MCP_CLIENTS, List.of("mcp1", "mcp2"));
      var toolCallResults =
          List.of(
              ToolCallResult.builder()
                  .id("MCP_toolsList_mcp1")
                  .name("mcp1")
                  .content("result1")
                  .build(),
              ToolCallResult.builder()
                  .id("MCP_toolsList_mcp2")
                  .name("mcp2")
                  .content("result2")
                  .build());

      assertThat(handler.allToolDiscoveryResultsPresent(agentContext, toolCallResults)).isTrue();
    }

    @Test
    void returnsFalse_whenSomeMcpClientToolDiscoveryResultsMissing() {
      var agentContext =
          AgentContext.empty().withProperty(PROPERTY_MCP_CLIENTS, List.of("mcp1", "mcp2"));
      var toolCallResults =
          List.of(
              ToolCallResult.builder()
                  .id("MCP_toolsList_mcp1")
                  .name("mcp1")
                  .content("result1")
                  .build());

      assertThat(handler.allToolDiscoveryResultsPresent(agentContext, toolCallResults)).isFalse();
    }

    @Test
    void returnsFalse_whenNoToolCallResultsProvided() {
      var agentContext =
          AgentContext.empty().withProperty(PROPERTY_MCP_CLIENTS, List.of("mcp1", "mcp2"));
      var toolCallResults = List.<ToolCallResult>of();

      assertThat(handler.allToolDiscoveryResultsPresent(agentContext, toolCallResults)).isFalse();
    }

    @Test
    void returnsTrue_whenNoMcpClientsConfigured() {
      var agentContext = AgentContext.empty();
      var toolCallResults = List.<ToolCallResult>of();

      assertThat(handler.allToolDiscoveryResultsPresent(agentContext, toolCallResults)).isTrue();
    }

    @Test
    void returnsTrue_whenEmptyMcpClientsList() {
      var agentContext = AgentContext.empty().withProperty(PROPERTY_MCP_CLIENTS, List.of());
      var toolCallResults = List.<ToolCallResult>of();

      assertThat(handler.allToolDiscoveryResultsPresent(agentContext, toolCallResults)).isTrue();
    }

    @Test
    void ignoresNonDiscoveryToolCallResults() {
      var agentContext = AgentContext.empty().withProperty(PROPERTY_MCP_CLIENTS, List.of("mcp1"));
      var toolCallResults =
          List.of(
              ToolCallResult.builder()
                  .id("MCP_toolsList_mcp1")
                  .name("mcp1")
                  .content("result1")
                  .build(),
              ToolCallResult.builder()
                  .id("regular_tool_call")
                  .name("regular_tool")
                  .content("result")
                  .build());

      assertThat(handler.allToolDiscoveryResultsPresent(agentContext, toolCallResults)).isTrue();
    }
  }

  @Nested
  class ToolDiscoveryResultHandling {

    @ParameterizedTest
    @MethodSource("toolDiscoveryResultScenarios")
    void handlesToolDiscoveryResult_correctly(String toolCallId, boolean expected) {
      var toolCallResult =
          ToolCallResult.builder().id(toolCallId).name("mcp1").content("result").build();

      assertThat(handler.handlesToolDiscoveryResult(toolCallResult)).isEqualTo(expected);
    }

    @Test
    void returnsFalse_whenToolCallResultIdIsBlank() {
      var toolCallResult = ToolCallResult.builder().id("").name("mcp1").content("result").build();

      assertThat(handler.handlesToolDiscoveryResult(toolCallResult)).isFalse();
    }

    @Test
    void convertsMcpDiscoveryResults_toToolDefinitions() {
      var agentContext = AgentContext.empty();
      var mcpListToolsResult =
          new McpClientListToolsResult(
              List.of(
                  createMcpToolDefinition("tool1", "Tool 1 description"),
                  createMcpToolDefinition("tool2", "Tool 2 description")));
      var toolDiscoveryResults =
          List.of(
              createToolCallResultWithContent("MCP_toolsList_mcp1", "mcp1", mcpListToolsResult));

      var result = handler.handleToolDiscoveryResults(agentContext, toolDiscoveryResults);

      assertThat(result).hasSize(2);
      assertThat(result)
          .satisfiesExactly(
              toolDefinition -> {
                assertThat(toolDefinition.name()).isEqualTo("MCP_mcp1___tool1");
                assertThat(toolDefinition.description()).isEqualTo("Tool 1 description");
              },
              toolDefinition -> {
                assertThat(toolDefinition.name()).isEqualTo("MCP_mcp1___tool2");
                assertThat(toolDefinition.description()).isEqualTo("Tool 2 description");
              });
    }

    @Test
    void handlesMultipleDiscoveryResults() {
      var agentContext = AgentContext.empty();
      var mcpListToolsResult1 =
          new McpClientListToolsResult(List.of(createMcpToolDefinition("tool1", "Tool 1")));
      var mcpListToolsResult2 =
          new McpClientListToolsResult(List.of(createMcpToolDefinition("tool2", "Tool 2")));
      var toolDiscoveryResults =
          List.of(
              createToolCallResultWithContent("MCP_toolsList_mcp1", "mcp1", mcpListToolsResult1),
              createToolCallResultWithContent("MCP_toolsList_mcp2", "mcp2", mcpListToolsResult2));

      var result = handler.handleToolDiscoveryResults(agentContext, toolDiscoveryResults);

      assertThat(result).hasSize(2);
      assertThat(result)
          .satisfiesExactly(
              toolDefinition -> assertThat(toolDefinition.name()).isEqualTo("MCP_mcp1___tool1"),
              toolDefinition -> assertThat(toolDefinition.name()).isEqualTo("MCP_mcp2___tool2"));
    }

    static Stream<Arguments> toolDiscoveryResultScenarios() {
      return Stream.of(
          arguments("MCP_toolsList_mcp1", true),
          arguments("MCP_toolsList_client", true),
          arguments("regular_tool_call", false),
          arguments("MCP_different_prefix", false),
          arguments(null, false));
    }
  }

  @Nested
  class ToolCallTransformation {

    @Test
    void transformsMcpToolCalls_toMcpClientOperations() {
      var agentContext = AgentContext.empty();
      var toolCalls =
          List.of(
              new ToolCall("call1", "MCP_mcp1___tool1", Map.of("arg1", "value1")),
              new ToolCall("call2", "regular_tool", Map.of("arg2", "value2")));

      var result = handler.transformToolCalls(agentContext, toolCalls);

      assertThat(result).hasSize(2);
      assertThat(result)
          .satisfiesExactly(
              toolCall -> {
                assertThat(toolCall.id()).isEqualTo("call1");
                assertThat(toolCall.name()).isEqualTo("mcp1");
                assertThat(toolCall.arguments()).containsKeys("method", "params");
                assertThat(toolCall.arguments().get("method")).isEqualTo("tools/call");
                assertThat(toolCall.arguments().get("params"))
                    .isEqualTo(Map.of("name", "tool1", "arguments", Map.of("arg1", "value1")));
              },
              toolCall -> {
                assertThat(toolCall.id()).isEqualTo("call2");
                assertThat(toolCall.name()).isEqualTo("regular_tool");
                assertThat(toolCall.arguments()).containsEntry("arg2", "value2");
              });
    }

    @Test
    void doesNotTransform_nonMcpToolCalls() {
      var agentContext = AgentContext.empty();
      var toolCalls =
          List.of(
              new ToolCall("call1", "regular_tool1", Map.of("arg1", "value1")),
              new ToolCall("call2", "regular_tool2", Map.of("arg2", "value2")));

      var result = handler.transformToolCalls(agentContext, toolCalls);

      assertThat(result).isEqualTo(toolCalls);
    }
  }

  @Nested
  class ToolCallResultTransformation {

    @Test
    void transformsMcpClientResults_toMcpToolCallResults() {
      var agentContext =
          AgentContext.empty().withProperty(PROPERTY_MCP_CLIENTS, List.of("mcp1", "mcp2"));
      var mcpCallToolResult =
          new McpClientCallToolResult(
              "tool1", List.of(McpTextContent.textContent("Tool result")), false);
      var toolCallResults =
          List.of(
              createToolCallResultWithContent("call1", "mcp1", mcpCallToolResult),
              createToolCallResult("call2", "regular_tool"));

      var result = handler.transformToolCallResults(agentContext, toolCallResults);

      assertThat(result).hasSize(2);
      assertThat(result)
          .satisfiesExactly(
              toolCallResult -> {
                assertThat(toolCallResult.id()).isEqualTo("call1");
                assertThat(toolCallResult.name()).isEqualTo("MCP_mcp1___tool1");
                assertThat(toolCallResult.content()).isEqualTo("Tool result");
              },
              toolCallResult -> {
                assertThat(toolCallResult.id()).isEqualTo("call2");
                assertThat(toolCallResult.name()).isEqualTo("regular_tool");
              });
    }

    @Test
    void retainsListOfContentBlocksIfResultIsNotASingleTextBlock() {
      var agentContext = AgentContext.empty().withProperty(PROPERTY_MCP_CLIENTS, List.of("mcp1"));
      var mcpCallToolResult =
          new McpClientCallToolResult(
              "tool1",
              List.of(
                  McpTextContent.textContent("First content"),
                  McpTextContent.textContent("Second content")),
              false);
      // simulate engine deserialization: content arrives as a raw Map, not a typed POJO
      @SuppressWarnings("unchecked")
      var contentAsMap = objectMapper.convertValue(mcpCallToolResult, Map.class);
      var toolCallResults = List.of(createToolCallResultWithContent("call1", "mcp1", contentAsMap));

      var result = handler.transformToolCallResults(agentContext, toolCallResults);

      assertThat(result).hasSize(1);
      // getRawMcpContent extracts the "content" key from the map, preserving the raw list
      assertThat(result.getFirst().content())
          .asInstanceOf(InstanceOfAssertFactories.LIST)
          .hasSize(2)
          .satisfiesExactly(
              first ->
                  assertThat(first)
                      .asInstanceOf(InstanceOfAssertFactories.MAP)
                      .containsEntry("text", "First content"),
              second ->
                  assertThat(second)
                      .asInstanceOf(InstanceOfAssertFactories.MAP)
                      .containsEntry("text", "Second content"));
    }

    @Test
    void preservesOriginalResult_whenNotMcpClient() {
      var agentContext = AgentContext.empty().withProperty(PROPERTY_MCP_CLIENTS, List.of("mcp1"));
      var toolCallResults = List.of(createToolCallResult("call1", "other_tool"));

      var result = handler.transformToolCallResults(agentContext, toolCallResults);

      assertThat(result).isEqualTo(toolCallResults);
    }

    @Test
    void handlesEmptyMcpClientsList() {
      var agentContext = AgentContext.empty();
      var toolCallResults = List.of(createToolCallResult("call1", "mcp1"));

      var result = handler.transformToolCallResults(agentContext, toolCallResults);

      assertThat(result).isEqualTo(toolCallResults);
    }
  }

  private GatewayToolDefinition createGatewayToolDefinition(String type, String name) {
    return GatewayToolDefinition.builder()
        .type(type)
        .name(name)
        .description("Description for " + name)
        .properties(Map.of())
        .build();
  }

  private ToolCallResult createToolCallResult(String id, String name) {
    return ToolCallResult.builder().id(id).name(name).content("result content").build();
  }

  private ToolCallResult createToolCallResultWithContent(String id, String name, Object content) {
    return ToolCallResult.builder().id(id).name(name).content(content).build();
  }

  private McpToolDefinition createMcpToolDefinition(String name, String description) {
    return McpToolDefinitionBuilder.builder()
        .name(name)
        .description(description)
        .inputSchema(Map.of("type", "object"))
        .build();
  }

  @Nested
  class ResolveUpdatedGatewayToolDefinitions {

    @Test
    void returnsEmptyUpdates_whenNoChanges() {
      var agentContext =
          AgentContext.empty().withProperty(PROPERTY_MCP_CLIENTS, List.of("mcp1", "mcp2"));
      var gatewayToolDefinitions =
          List.of(
              createGatewayToolDefinition("mcpClient", "mcp1"),
              createGatewayToolDefinition("mcpClient", "mcp2"));

      var result =
          handler.resolveUpdatedGatewayToolDefinitions(agentContext, gatewayToolDefinitions);

      assertThat(result.added()).isEmpty();
      assertThat(result.removed()).isEmpty();
    }

    @Test
    void detectsAddedMcpClients() {
      var agentContext = AgentContext.empty().withProperty(PROPERTY_MCP_CLIENTS, List.of("mcp1"));
      var gatewayToolDefinitions =
          List.of(
              createGatewayToolDefinition("mcpClient", "mcp1"),
              createGatewayToolDefinition("mcpClient", "mcp2"),
              createGatewayToolDefinition("mcpClient", "mcp3"));

      var result =
          handler.resolveUpdatedGatewayToolDefinitions(agentContext, gatewayToolDefinitions);

      assertThat(result.added()).containsExactly("mcp2", "mcp3");
      assertThat(result.removed()).isEmpty();
    }

    @Test
    void detectsRemovedMcpClients() {
      var agentContext =
          AgentContext.empty().withProperty(PROPERTY_MCP_CLIENTS, List.of("mcp1", "mcp2", "mcp3"));
      var gatewayToolDefinitions = List.of(createGatewayToolDefinition("mcpClient", "mcp1"));

      var result =
          handler.resolveUpdatedGatewayToolDefinitions(agentContext, gatewayToolDefinitions);

      assertThat(result.added()).isEmpty();
      assertThat(result.removed()).containsExactly("mcp2", "mcp3");
    }

    @Test
    void detectsBothAddedAndRemovedMcpClients() {
      var agentContext =
          AgentContext.empty().withProperty(PROPERTY_MCP_CLIENTS, List.of("mcp1", "mcp2"));
      var gatewayToolDefinitions =
          List.of(
              createGatewayToolDefinition("mcpClient", "mcp2"),
              createGatewayToolDefinition("mcpClient", "mcp3"));

      var result =
          handler.resolveUpdatedGatewayToolDefinitions(agentContext, gatewayToolDefinitions);

      assertThat(result.added()).containsExactly("mcp3");
      assertThat(result.removed()).containsExactly("mcp1");
    }

    @Test
    void ignoresNonMcpGatewayToolDefinitions() {
      var agentContext = AgentContext.empty().withProperty(PROPERTY_MCP_CLIENTS, List.of("mcp1"));
      var gatewayToolDefinitions =
          List.of(
              createGatewayToolDefinition("mcpClient", "mcp1"),
              createGatewayToolDefinition("other", "other1"),
              createGatewayToolDefinition("a2aClient", "a2a1"));

      var result =
          handler.resolveUpdatedGatewayToolDefinitions(agentContext, gatewayToolDefinitions);

      assertThat(result.added()).isEmpty();
      assertThat(result.removed()).isEmpty();
    }

    @Test
    void handlesEmptyExistingClients() {
      var agentContext = AgentContext.empty();
      var gatewayToolDefinitions =
          List.of(
              createGatewayToolDefinition("mcpClient", "mcp1"),
              createGatewayToolDefinition("mcpClient", "mcp2"));

      var result =
          handler.resolveUpdatedGatewayToolDefinitions(agentContext, gatewayToolDefinitions);

      assertThat(result.added()).containsExactly("mcp1", "mcp2");
      assertThat(result.removed()).isEmpty();
    }

    @Test
    void handlesEmptyNewGatewayToolDefinitions() {
      var agentContext =
          AgentContext.empty().withProperty(PROPERTY_MCP_CLIENTS, List.of("mcp1", "mcp2"));
      var gatewayToolDefinitions = List.<GatewayToolDefinition>of();

      var result =
          handler.resolveUpdatedGatewayToolDefinitions(agentContext, gatewayToolDefinitions);

      assertThat(result.added()).isEmpty();
      assertThat(result.removed()).containsExactly("mcp1", "mcp2");
    }
  }
}
