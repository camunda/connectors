/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agentinstance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AgentInstanceToolMapperTest {

  private final GatewayToolHandlerRegistry gatewayToolHandlers =
      mock(GatewayToolHandlerRegistry.class);
  private final AgentInstanceToolMapper mapper = new AgentInstanceToolMapper(gatewayToolHandlers);

  @Test
  void adHocToolFallsBackToItsNameAsElementId() {
    when(gatewayToolHandlers.resolveElementId("getWeather")).thenReturn(Optional.empty());
    final var tool =
        ToolDefinition.builder()
            .name("getWeather")
            .description("Get the weather forecast")
            .inputSchema(Map.of("type", "object"))
            .build();

    final var mapped = mapper.mapTools(List.of(tool));

    assertThat(mapped)
        .singleElement()
        .satisfies(
            agentTool -> {
              assertThat(agentTool.getName()).isEqualTo("getWeather");
              assertThat(agentTool.getDescription()).isEqualTo("Get the weather forecast");
              assertThat(agentTool.getElementId()).isEqualTo("getWeather");
            });
  }

  @Test
  void gatewayToolResolvesElementIdFromTheRegistry() {
    when(gatewayToolHandlers.resolveElementId("MCP_McpTest___greet"))
        .thenReturn(Optional.of("McpTest"));
    final var tool =
        ToolDefinition.builder()
            .name("MCP_McpTest___greet")
            .description("Greet someone")
            .inputSchema(Map.of("type", "object"))
            .build();

    final var mapped = mapper.mapTools(List.of(tool));

    assertThat(mapped)
        .singleElement()
        .satisfies(
            agentTool -> {
              assertThat(agentTool.getName()).isEqualTo("MCP_McpTest___greet");
              assertThat(agentTool.getDescription()).isEqualTo("Greet someone");
              assertThat(agentTool.getElementId()).isEqualTo("McpTest");
            });
  }
}
