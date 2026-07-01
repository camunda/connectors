/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.discovery;

import static io.camunda.connector.agenticai.adhoctoolsschema.schema.GatewayToolDefinitionResolver.GATEWAY_TYPE_EXTENSION;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolElement;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class McpClientGatewayToolDefinitionResolverTest {

  private McpClientGatewayToolDefinitionResolver resolver;

  @BeforeEach
  void setUp() {
    resolver = new McpClientGatewayToolDefinitionResolver();
  }

  @Test
  void returnsEmptyList_whenNoMcpClientElements() {
    final var elements =
        List.of(createElement("task1", "Task 1"), createElement("task2", "Task 2"));

    final var result = resolver.resolveGatewayToolDefinitions(elements);

    assertThat(result).isEmpty();
  }

  @Test
  void resolvesGatewayToolDefinitionsFromElementsWithMcpClientProperty() {
    final var elements =
        List.of(
            withMcpClientProperty(createElement("mcp-task-1", "MCP Task 1")),
            withMcpClientProperty(createElement("mcp-task-2", "MCP Task 2")),
            createElement("other-task", "Other task"),
            withMcpClientProperty(
                createElement("mcp-task-3", "MCP Task 3", "Custom documentation")));

    final var result = resolver.resolveGatewayToolDefinitions(elements);

    assertThat(result)
        .hasSize(3)
        .satisfiesExactly(
            gatewayToolDefinition -> {
              assertThat(gatewayToolDefinition.type()).isEqualTo("mcpClient");
              assertThat(gatewayToolDefinition.name()).isEqualTo("mcp-task-1");
              assertThat(gatewayToolDefinition.description()).isEqualTo("MCP Task 1");
            },
            gatewayToolDefinition -> {
              assertThat(gatewayToolDefinition.type()).isEqualTo("mcpClient");
              assertThat(gatewayToolDefinition.name()).isEqualTo("mcp-task-2");
              assertThat(gatewayToolDefinition.description()).isEqualTo("MCP Task 2");
            },
            gatewayToolDefinition -> {
              assertThat(gatewayToolDefinition.type()).isEqualTo("mcpClient");
              assertThat(gatewayToolDefinition.name()).isEqualTo("mcp-task-3");
              assertThat(gatewayToolDefinition.description()).isEqualTo("Custom documentation");
            });
  }

  private AdHocToolElement createElement(String id, String name) {
    return AdHocToolElement.builder().elementId(id).elementName(name).build();
  }

  private AdHocToolElement createElement(String id, String name, String documentation) {
    return createElement(id, name).withDocumentation(documentation);
  }

  private AdHocToolElement withMcpClientProperty(AdHocToolElement element) {
    return element.withProperties(
        Map.of(GATEWAY_TYPE_EXTENSION, McpClientGatewayToolHandler.GATEWAY_TYPE));
  }
}
