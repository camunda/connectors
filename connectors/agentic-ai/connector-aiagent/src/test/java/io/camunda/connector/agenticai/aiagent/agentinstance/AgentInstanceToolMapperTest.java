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

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentFeatures;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolRegistry;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolRegistryImpl;
import io.camunda.connector.agenticai.aiagent.tool.runtime.StubRuntimeTool;
import io.camunda.connector.agenticai.aiagent.tool.runtime.builtin.ReturnDocumentTool;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AgentInstanceToolMapperTest {

  private static final AgentContext NEW_INSTANCE =
      AgentContext.empty().withFeatures(AgentFeatures.ofNewInstance());

  private static final ToolDefinition RUNTIME_TOOL =
      ToolDefinition.builder()
          .name("acme_lookup")
          .description("Looks up a customer")
          .inputSchema(Map.of("type", "object"))
          .build();

  private final GatewayToolHandlerRegistry gatewayToolHandlers =
      mock(GatewayToolHandlerRegistry.class);
  private final RuntimeToolRegistry runtimeToolRegistry =
      new RuntimeToolRegistryImpl(
          List.of(StubRuntimeTool.of(RUNTIME_TOOL, (call, context) -> null)),
          TestObjectMapperSupplier.INSTANCE);
  private final AgentInstanceToolMapper mapper =
      new AgentInstanceToolMapper(gatewayToolHandlers, runtimeToolRegistry);

  @Test
  void adHocToolFallsBackToItsNameAsElementId() {
    when(gatewayToolHandlers.resolveElementId("getWeather")).thenReturn(Optional.empty());
    final var tool =
        ToolDefinition.builder()
            .name("getWeather")
            .description("Get the weather forecast")
            .inputSchema(Map.of("type", "object"))
            .build();

    final var mapped = mapper.mapTools(NEW_INSTANCE, List.of(tool));

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
  void runtimeToolHasNoElementId() {
    final var mapped = mapper.mapTools(NEW_INSTANCE, List.of(RUNTIME_TOOL));

    assertThat(mapped)
        .singleElement()
        .satisfies(
            agentTool -> {
              assertThat(agentTool.getName()).isEqualTo("acme_lookup");
              assertThat(agentTool.getDescription()).isEqualTo("Looks up a customer");
              assertThat(agentTool.getElementId()).isNull();
            });
  }

  @Test
  void theBuiltInReturnDocumentToolHasNoElementId() {
    final var registry =
        new RuntimeToolRegistryImpl(
            List.of(new ReturnDocumentTool()), TestObjectMapperSupplier.INSTANCE);

    final var mapped =
        new AgentInstanceToolMapper(gatewayToolHandlers, registry)
            .mapTools(NEW_INSTANCE, List.of(new ReturnDocumentTool().definition()));

    assertThat(mapped)
        .singleElement()
        .satisfies(
            agentTool -> {
              assertThat(agentTool.getName()).isEqualTo("c8__return_document");
              assertThat(agentTool.getElementId()).isNull();
            });
  }

  @Test
  void aToolOfTheProcessWithTheNameOfARuntimeToolKeepsItsElementIdWithoutTheRuntimeToolsFeature() {
    when(gatewayToolHandlers.resolveElementId("acme_lookup")).thenReturn(Optional.empty());

    final var mapped = mapper.mapTools(AgentContext.empty(), List.of(RUNTIME_TOOL));

    assertThat(mapped)
        .singleElement()
        .satisfies(agentTool -> assertThat(agentTool.getElementId()).isEqualTo("acme_lookup"));
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

    final var mapped = mapper.mapTools(NEW_INSTANCE, List.of(tool));

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
