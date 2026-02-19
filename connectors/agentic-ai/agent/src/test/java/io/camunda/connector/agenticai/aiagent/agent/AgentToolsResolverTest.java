/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolsSchemaResponse;
import io.camunda.connector.agenticai.adhoctoolsschema.schema.AdHocToolsSchemaResolver;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolDefinitionUpdates;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.model.tool.GatewayToolDefinition;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import io.camunda.connector.api.error.ConnectorException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentToolsResolverTest {

  @Mock private AdHocToolsSchemaResolver toolsSchemaResolver;
  @Mock private GatewayToolHandlerRegistry gatewayToolHandlers;
  @Mock private AgentExecutionContext executionContext;

  private AgentToolsResolverImpl agentToolsResolver;

  @BeforeEach
  void setUp() {
    agentToolsResolver = new AgentToolsResolverImpl(toolsSchemaResolver, gatewayToolHandlers);
  }

  @Nested
  class UpdateToolDefinitions {

    private static final ToolDefinition TOOL_1 =
        ToolDefinition.builder()
            .name("tool1")
            .description("Tool 1 description")
            .inputSchema(Map.of("type", "object"))
            .build();

    private static final ToolDefinition TOOL_2 =
        ToolDefinition.builder()
            .name("tool2")
            .description("Tool 2 description")
            .inputSchema(Map.of("type", "object"))
            .build();

    private static final ToolDefinition TOOL_3 =
        ToolDefinition.builder()
            .name("tool3")
            .description("Tool 3 description")
            .inputSchema(Map.of("type", "object"))
            .build();

    private static final ToolDefinition MCP_MANAGED_TOOL =
        ToolDefinition.builder()
            .name("MCP_mcpClient___remoteTool")
            .description("MCP managed tool")
            .inputSchema(Map.of("type", "object"))
            .build();

    @Test
    void addsNewToolDefinitions() {
      final var existingTools = List.of(TOOL_1);
      final var agentContext = AgentContext.empty().withToolDefinitions(existingTools);

      final var newTools = List.of(TOOL_1, TOOL_2);
      when(toolsSchemaResolver.resolveAdHocToolsSchema(any()))
          .thenReturn(new AdHocToolsSchemaResponse(newTools, List.of()));
      when(gatewayToolHandlers.resolveUpdatedGatewayToolDefinitions(any(), any()))
          .thenReturn(Map.of());
      when(gatewayToolHandlers.isGatewayManaged(any())).thenReturn(false);

      final var result = agentToolsResolver.updateToolDefinitions(executionContext, agentContext);

      assertThat(result.toolDefinitions()).hasSize(2);
      assertThat(result.toolDefinitions())
          .extracting(ToolDefinition::name)
          .containsExactly("tool1", "tool2");
    }

    @Test
    void updatesExistingToolDefinitions() {
      final var existingTools = List.of(TOOL_1);
      final var agentContext = AgentContext.empty().withToolDefinitions(existingTools);

      final var updatedTool1 =
          ToolDefinition.builder()
              .name("tool1")
              .description("Updated Tool 1 description")
              .inputSchema(Map.of("type", "object", "properties", Map.of()))
              .build();
      final var newTools = List.of(updatedTool1);
      when(toolsSchemaResolver.resolveAdHocToolsSchema(any()))
          .thenReturn(new AdHocToolsSchemaResponse(newTools, List.of()));
      when(gatewayToolHandlers.resolveUpdatedGatewayToolDefinitions(any(), any()))
          .thenReturn(Map.of());
      when(gatewayToolHandlers.isGatewayManaged(any())).thenReturn(false);

      final var result = agentToolsResolver.updateToolDefinitions(executionContext, agentContext);

      assertThat(result.toolDefinitions()).hasSize(1);
      assertThat(result.toolDefinitions().get(0).description())
          .isEqualTo("Updated Tool 1 description");
    }

    @Test
    void throwsErrorWhenToolsAreRemoved() {
      final var existingTools = List.of(TOOL_1, TOOL_2);
      final var agentContext = AgentContext.empty().withToolDefinitions(existingTools);

      // Only TOOL_1 remains, TOOL_2 is removed
      final var newTools = List.of(TOOL_1);
      when(toolsSchemaResolver.resolveAdHocToolsSchema(any()))
          .thenReturn(new AdHocToolsSchemaResponse(newTools, List.of()));
      when(gatewayToolHandlers.resolveUpdatedGatewayToolDefinitions(any(), any()))
          .thenReturn(Map.of());
      when(gatewayToolHandlers.isGatewayManaged(any())).thenReturn(false);

      assertThatThrownBy(
              () -> agentToolsResolver.updateToolDefinitions(executionContext, agentContext))
          .isInstanceOf(ConnectorException.class)
          .hasFieldOrPropertyWithValue(
              "errorCode", AgentErrorCodes.ERROR_CODE_MIGRATION_MISSING_TOOLS)
          .hasMessage(
              """
                  The AI Agent references tools that are no longer defined, most likely due to a process migration.
                  Removing or renaming existing tools is currently not supported.
                  Please re-add the following tools to continue agent execution: tool2""");
    }

    @Test
    void throwsErrorWhenGatewayToolDefinitionAdded() {
      final var existingTools = List.of(TOOL_1);
      final var agentContext = AgentContext.empty().withToolDefinitions(existingTools);

      final var gatewayToolDefinitions =
          List.of(
              GatewayToolDefinition.builder()
                  .type("mcpClient")
                  .name("newMcpClient")
                  .description("New MCP client added")
                  .build());
      when(toolsSchemaResolver.resolveAdHocToolsSchema(any()))
          .thenReturn(new AdHocToolsSchemaResponse(List.of(TOOL_1), gatewayToolDefinitions));

      // Simulate gateway changes detected - new MCP client added
      when(gatewayToolHandlers.resolveUpdatedGatewayToolDefinitions(any(), any()))
          .thenReturn(
              Map.of(
                  "mcpClient",
                  new GatewayToolDefinitionUpdates(List.of("newMcpClient"), List.of())));

      assertThatThrownBy(
              () -> agentToolsResolver.updateToolDefinitions(executionContext, agentContext))
          .isInstanceOf(ConnectorException.class)
          .hasFieldOrPropertyWithValue(
              "errorCode", AgentErrorCodes.ERROR_CODE_MIGRATION_GATEWAY_TOOL_DEFINITIONS_CHANGED)
          .hasMessage(
              """
                  Gateway tool definitions have changed, most likely due to a process migration.
                  Adding or removing gateway tool definitions to a running AI Agent is currently not supported.
                  Please restore gateway tool definitions to the previous state to continue agent execution.
                  Changes: mcpClient [added: newMcpClient]""");
    }

    @Test
    void throwsErrorWhenGatewayToolDefinitionRemoved() {
      final var existingTools = List.of(TOOL_1, MCP_MANAGED_TOOL);
      final var agentContext = AgentContext.empty().withToolDefinitions(existingTools);

      // No gateway tool definitions in new schema - the existing MCP client was removed
      when(toolsSchemaResolver.resolveAdHocToolsSchema(any()))
          .thenReturn(new AdHocToolsSchemaResponse(List.of(TOOL_1), List.of()));

      // Simulate gateway changes detected - MCP client removed
      when(gatewayToolHandlers.resolveUpdatedGatewayToolDefinitions(any(), any()))
          .thenReturn(
              Map.of(
                  "mcpClient", new GatewayToolDefinitionUpdates(List.of(), List.of("mcpClient"))));

      assertThatThrownBy(
              () -> agentToolsResolver.updateToolDefinitions(executionContext, agentContext))
          .isInstanceOf(ConnectorException.class)
          .hasFieldOrPropertyWithValue(
              "errorCode", AgentErrorCodes.ERROR_CODE_MIGRATION_GATEWAY_TOOL_DEFINITIONS_CHANGED)
          .hasMessage(
              """
                  Gateway tool definitions have changed, most likely due to a process migration.
                  Adding or removing gateway tool definitions to a running AI Agent is currently not supported.
                  Please restore gateway tool definitions to the previous state to continue agent execution.
                  Changes: mcpClient [removed: mcpClient]""");
    }

    @Test
    void throwsErrorWhenGatewayToolDefinitionAddedAndRemoved() {
      final var existingTools = List.of(TOOL_1, MCP_MANAGED_TOOL);
      final var agentContext = AgentContext.empty().withToolDefinitions(existingTools);

      // MCP client was renamed (old one removed, new one added)
      final var gatewayToolDefinitions =
          List.of(
              GatewayToolDefinition.builder()
                  .type("mcpClient")
                  .name("renamedMcpClient")
                  .description("Renamed MCP client")
                  .build());
      when(toolsSchemaResolver.resolveAdHocToolsSchema(any()))
          .thenReturn(new AdHocToolsSchemaResponse(List.of(TOOL_1), gatewayToolDefinitions));

      // Simulate gateway changes detected - one MCP client removed, another added
      when(gatewayToolHandlers.resolveUpdatedGatewayToolDefinitions(any(), any()))
          .thenReturn(
              Map.of(
                  "mcpClient",
                  new GatewayToolDefinitionUpdates(
                      List.of("renamedMcpClient"), List.of("mcpClient"))));

      assertThatThrownBy(
              () -> agentToolsResolver.updateToolDefinitions(executionContext, agentContext))
          .isInstanceOf(ConnectorException.class)
          .hasFieldOrPropertyWithValue(
              "errorCode", AgentErrorCodes.ERROR_CODE_MIGRATION_GATEWAY_TOOL_DEFINITIONS_CHANGED)
          .hasMessage(
              """
                  Gateway tool definitions have changed, most likely due to a process migration.
                  Adding or removing gateway tool definitions to a running AI Agent is currently not supported.
                  Please restore gateway tool definitions to the previous state to continue agent execution.
                  Changes: mcpClient [added: renamedMcpClient; removed: mcpClient]""");
    }

    @Test
    void throwsErrorWhenGatewayToolDefinitionTypeChanged() {
      final var existingTools = List.of(TOOL_1, MCP_MANAGED_TOOL);
      final var agentContext = AgentContext.empty().withToolDefinitions(existingTools);

      // Gateway tool definition changed from mcpClient to a2aClient
      final var gatewayToolDefinitions =
          List.of(
              GatewayToolDefinition.builder()
                  .type("a2aClient")
                  .name("mcpClient") // Same name but different type
                  .description("Changed to A2A client")
                  .build());
      when(toolsSchemaResolver.resolveAdHocToolsSchema(any()))
          .thenReturn(new AdHocToolsSchemaResponse(List.of(TOOL_1), gatewayToolDefinitions));

      // Simulate gateway changes detected - removed from MCP, added to A2A
      final var gatewayUpdates = new LinkedHashMap<String, GatewayToolDefinitionUpdates>();
      gatewayUpdates.put(
          "mcpClient", new GatewayToolDefinitionUpdates(List.of(), List.of("mcpClient")));
      gatewayUpdates.put(
          "a2aClient", new GatewayToolDefinitionUpdates(List.of("mcpClient"), List.of()));
      when(gatewayToolHandlers.resolveUpdatedGatewayToolDefinitions(any(), any()))
          .thenReturn(gatewayUpdates);

      assertThatThrownBy(
              () -> agentToolsResolver.updateToolDefinitions(executionContext, agentContext))
          .isInstanceOf(ConnectorException.class)
          .hasFieldOrPropertyWithValue(
              "errorCode", AgentErrorCodes.ERROR_CODE_MIGRATION_GATEWAY_TOOL_DEFINITIONS_CHANGED)
          .hasMessage(
              """
                  Gateway tool definitions have changed, most likely due to a process migration.
                  Adding or removing gateway tool definitions to a running AI Agent is currently not supported.
                  Please restore gateway tool definitions to the previous state to continue agent execution.
                  Changes: mcpClient [removed: mcpClient], a2aClient [added: mcpClient]""");
    }

    @Test
    void preservesGatewayManagedToolDefinitions() {
      final var existingTools = List.of(TOOL_1, MCP_MANAGED_TOOL);
      final var agentContext = AgentContext.empty().withToolDefinitions(existingTools);

      // Gateway-managed tool is not included in new schema (as expected - they come from discovery)
      final var newTools = List.of(TOOL_1, TOOL_2);
      when(toolsSchemaResolver.resolveAdHocToolsSchema(any()))
          .thenReturn(new AdHocToolsSchemaResponse(newTools, List.of()));
      when(gatewayToolHandlers.resolveUpdatedGatewayToolDefinitions(any(), any()))
          .thenReturn(Map.of());
      when(gatewayToolHandlers.isGatewayManaged("tool1")).thenReturn(false);
      when(gatewayToolHandlers.isGatewayManaged("MCP_mcpClient___remoteTool")).thenReturn(true);

      final var result = agentToolsResolver.updateToolDefinitions(executionContext, agentContext);

      // Should have existing tool1, MCP managed tool (preserved), and new tool2
      assertThat(result.toolDefinitions()).hasSize(3);
      assertThat(result.toolDefinitions())
          .extracting(ToolDefinition::name)
          .containsExactly("tool1", "MCP_mcpClient___remoteTool", "tool2");
    }

    @Test
    void addsAndUpdatesToolsSimultaneously() {
      final var existingTools = List.of(TOOL_1, TOOL_2);
      final var agentContext = AgentContext.empty().withToolDefinitions(existingTools);

      final var updatedTool1 =
          ToolDefinition.builder()
              .name("tool1")
              .description("Updated Tool 1")
              .inputSchema(Map.of("type", "object"))
              .build();
      final var newTools = List.of(updatedTool1, TOOL_2, TOOL_3);
      when(toolsSchemaResolver.resolveAdHocToolsSchema(any()))
          .thenReturn(new AdHocToolsSchemaResponse(newTools, List.of()));
      when(gatewayToolHandlers.resolveUpdatedGatewayToolDefinitions(any(), any()))
          .thenReturn(Map.of());
      when(gatewayToolHandlers.isGatewayManaged(any())).thenReturn(false);

      final var result = agentToolsResolver.updateToolDefinitions(executionContext, agentContext);

      assertThat(result.toolDefinitions()).hasSize(3);
      assertThat(result.toolDefinitions())
          .extracting(ToolDefinition::name)
          .containsExactly("tool1", "tool2", "tool3");
      assertThat(result.toolDefinitions().get(0).description()).isEqualTo("Updated Tool 1");
    }
  }
}
