/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.tool;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TOOL_CALLS;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TOOL_CALL_RESULTS;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TOOL_DEFINITIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.model.tool.GatewayToolDefinition;
import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GatewayToolHandlerRegistryTest {

  @Nested
  class Registration {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void throwsExceptionOnCreatingRegistry_whenHandlerHasEmptyType(String type) {
      assertThatThrownBy(
              () -> new GatewayToolHandlerRegistryImpl(List.of(gatewayToolHandler(type))))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Invalid gateway tool handler type: '%s'".formatted(type));
    }

    @Test
    void throwsExceptionOnCreatingRegistry_whenHandlerHasDefaultType() {
      assertThatThrownBy(
              () -> new GatewayToolHandlerRegistryImpl(List.of(gatewayToolHandler("_default"))))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Invalid gateway tool handler type: '_default'");
    }

    @Test
    void throwsExceptionOnCreatingRegistry_whenHandlerHasDuplicateType() {
      assertThatThrownBy(
              () ->
                  new GatewayToolHandlerRegistryImpl(
                      List.of(gatewayToolHandler("A"), gatewayToolHandler("A"))))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Duplicate gateway tool handler type: 'A'");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void throwsExceptionOnRegisteringHandler_whenHandlerHasEmptyType(String type) {
      final var registry = new GatewayToolHandlerRegistryImpl();
      assertThatThrownBy(() -> registry.register(gatewayToolHandler(type)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Invalid gateway tool handler type: '%s'".formatted(type));
    }

    @Test
    void throwsExceptionOnRegisteringHandler_whenHandlerHasDefaultType() {
      final var registry = new GatewayToolHandlerRegistryImpl();
      assertThatThrownBy(() -> registry.register(gatewayToolHandler("_default")))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Invalid gateway tool handler type: '_default'");
    }

    @Test
    void throwsExceptionOnRegisteringHandler_whenHandlerHasDuplicateType() {
      final var registry = new GatewayToolHandlerRegistryImpl();
      registry.register(gatewayToolHandler("A"));

      assertThatThrownBy(() -> registry.register(gatewayToolHandler("A")))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Duplicate gateway tool handler type: 'A'");
    }

    private GatewayToolHandler gatewayToolHandler(String type) {
      final var handler = mock(GatewayToolHandler.class);
      when(handler.type()).thenReturn(type);
      return handler;
    }
  }

  @Nested
  class Execution {

    private static final AgentContext AGENT_CONTEXT =
        AgentContext.empty().withToolDefinitions(TOOL_DEFINITIONS).withProperty("some", "property");

    private static final List<GatewayToolDefinition> GATEWAY_TOOL_DEFINITIONS =
        List.of(
            GatewayToolDefinition.builder()
                .type("mcpClient")
                .name("GatewayA")
                .description("The gateway handled by handler A.")
                .build(),
            GatewayToolDefinition.builder()
                .type("anotherType")
                .name("GatewayB")
                .description("Some other gateway type handled by handler B.")
                .build());

    private static final List<ToolDefinition> GATEWAY_A_TOOL_DEFINITIONS =
        List.of(
            ToolDefinition.builder().name("AFirstTool").description("Gateway A first tool").build(),
            ToolDefinition.builder()
                .name("ASecondTool")
                .description("Gateway A second tool")
                .inputSchema(Map.of("type", "object"))
                .build());

    private static final List<ToolDefinition> GATEWAY_B_TOOL_DEFINITIONS =
        List.of(
            ToolDefinition.builder().name("BFirstTool").description("Gateway B first tool").build(),
            ToolDefinition.builder()
                .name("BSecondTool")
                .description("Gateway B second tool")
                .build());

    @Mock private GatewayToolHandler handlerA;
    @Mock private GatewayToolHandler handlerB;

    private GatewayToolHandlerRegistry registry;

    @BeforeEach
    void setUp() {
      when(handlerA.type()).thenReturn("handlerA");
      when(handlerB.type()).thenReturn("handlerB");
      registry = new GatewayToolHandlerRegistryImpl(List.of(handlerA, handlerB));
    }

    @Nested
    class InitiateToolDiscovery {

      @Test
      void returnsUnmodifiedAgentContext_whenNoHandlersAreRegistered() {
        final var emptyRegistry = new GatewayToolHandlerRegistryImpl();
        final var result =
            emptyRegistry.initiateToolDiscovery(AGENT_CONTEXT, GATEWAY_TOOL_DEFINITIONS);

        assertThat(result.agentContext()).isEqualTo(AGENT_CONTEXT);
        assertThat(result.toolDiscoveryToolCalls()).isEmpty();
      }

      @Test
      void collectsToolDiscoveryToolCallsAndReturnsUpdatedAgentContext() {
        final var handlerAToolDiscoveryToolCall =
            ToolCall.builder().id("handlerA_discovery").name("GatewayA").build();
        when(handlerA.initiateToolDiscovery(any(AgentContext.class), eq(GATEWAY_TOOL_DEFINITIONS)))
            .thenAnswer(
                i ->
                    new GatewayToolDiscoveryInitiationResult(
                        i.getArgument(0, AgentContext.class).withProperty("handlerA", "was here"),
                        List.of(handlerAToolDiscoveryToolCall)));

        final var handlerBToolDiscoveryToolCall =
            ToolCall.builder().id("handlerB_discovery").name("GatewayB").build();
        when(handlerB.initiateToolDiscovery(any(AgentContext.class), eq(GATEWAY_TOOL_DEFINITIONS)))
            .thenAnswer(
                i ->
                    new GatewayToolDiscoveryInitiationResult(
                        i.getArgument(0, AgentContext.class)
                            .withProperty("handlerB", "was here as well"),
                        List.of(handlerBToolDiscoveryToolCall)));

        final var result = registry.initiateToolDiscovery(AGENT_CONTEXT, GATEWAY_TOOL_DEFINITIONS);

        assertThat(result.agentContext())
            .isEqualTo(
                AGENT_CONTEXT
                    .withProperty("handlerA", "was here")
                    .withProperty("handlerB", "was here as well"));
        assertThat(result.toolDiscoveryToolCalls())
            .containsExactly(handlerAToolDiscoveryToolCall, handlerBToolDiscoveryToolCall);
      }
    }

    @Nested
    class AllToolDiscoveryResultsPresent {

      private static final List<ToolCallResult> TOOL_CALL_RESULTS =
          List.of(
              ToolCallResult.builder().id("result1").name("tool1").content("content1").build(),
              ToolCallResult.builder().id("result2").name("tool2").content("content2").build());

      @Test
      void returnsTrue_whenAllHandlersReturnTrue() {
        when(handlerA.allToolDiscoveryResultsPresent(AGENT_CONTEXT, TOOL_CALL_RESULTS))
            .thenReturn(true);
        when(handlerB.allToolDiscoveryResultsPresent(AGENT_CONTEXT, TOOL_CALL_RESULTS))
            .thenReturn(true);

        assertThat(registry.allToolDiscoveryResultsPresent(AGENT_CONTEXT, TOOL_CALL_RESULTS))
            .isTrue();
      }

      @Test
      void returnsFalse_whenAnyHandlerReturnsFalse() {
        when(handlerA.allToolDiscoveryResultsPresent(AGENT_CONTEXT, TOOL_CALL_RESULTS))
            .thenReturn(true);
        when(handlerB.allToolDiscoveryResultsPresent(AGENT_CONTEXT, TOOL_CALL_RESULTS))
            .thenReturn(false);

        assertThat(registry.allToolDiscoveryResultsPresent(AGENT_CONTEXT, TOOL_CALL_RESULTS))
            .isFalse();
      }

      @Test
      void returnsFalse_whenFirstHandlerReturnsFalse() {
        when(handlerA.allToolDiscoveryResultsPresent(AGENT_CONTEXT, TOOL_CALL_RESULTS))
            .thenReturn(false);

        assertThat(registry.allToolDiscoveryResultsPresent(AGENT_CONTEXT, TOOL_CALL_RESULTS))
            .isFalse();
      }

      @Test
      void returnsTrue_whenNoHandlersAreRegistered() {
        final var emptyRegistry = new GatewayToolHandlerRegistryImpl();

        assertThat(emptyRegistry.allToolDiscoveryResultsPresent(AGENT_CONTEXT, TOOL_CALL_RESULTS))
            .isTrue();
      }
    }

    @Nested
    class HandleToolDiscoveryResults {

      @Test
      void delegatesToolDiscoveryResultsToHandlers_withoutRemainingToolCallResults() {
        testHandleToolDiscoveryResults(List.of());
      }

      @Test
      void delegatesToolDiscoveryResultsToHandlers_withRemainingToolCallResults() {
        testHandleToolDiscoveryResults(TOOL_CALL_RESULTS);
      }

      private void testHandleToolDiscoveryResults(
          List<ToolCallResult> nonDiscoveryToolCallResults) {
        // tool call results, mixed with 2 gateway results for handler A and one for handler B
        final var handlerAToolCallResults =
            List.of(
                ToolCallResult.builder()
                    .id("handlerA_discovery1")
                    .name("GatewayA1")
                    .content(Map.of("handlerAFormat", List.of(GATEWAY_A_TOOL_DEFINITIONS.get(0))))
                    .build(),
                ToolCallResult.builder()
                    .id("handlerA_discovery2")
                    .name("GatewayA2")
                    .content(Map.of("handlerAFormat", List.of(GATEWAY_A_TOOL_DEFINITIONS.get(1))))
                    .build());
        final var handlerBToolCallResults =
            List.of(
                ToolCallResult.builder()
                    .id("handlerB_discovery")
                    .name("GatewayB")
                    .content(Map.of("aDifferentHandlerBFormat", GATEWAY_B_TOOL_DEFINITIONS))
                    .build());

        final var toolCallResults = new ArrayList<>(nonDiscoveryToolCallResults);
        toolCallResults.addAll(handlerAToolCallResults);
        toolCallResults.addAll(handlerBToolCallResults);

        when(handlerA.handlesToolDiscoveryResult(any(ToolCallResult.class)))
            .thenAnswer(i -> i.getArgument(0, ToolCallResult.class).id().startsWith("handlerA_"));
        when(handlerA.handleToolDiscoveryResults(AGENT_CONTEXT, handlerAToolCallResults))
            .thenReturn(GATEWAY_A_TOOL_DEFINITIONS);

        when(handlerB.handlesToolDiscoveryResult(any(ToolCallResult.class)))
            .thenAnswer(i -> i.getArgument(0, ToolCallResult.class).id().startsWith("handlerB_"));
        when(handlerB.handleToolDiscoveryResults(AGENT_CONTEXT, handlerBToolCallResults))
            .thenReturn(GATEWAY_B_TOOL_DEFINITIONS);

        final var result = registry.handleToolDiscoveryResults(AGENT_CONTEXT, toolCallResults);

        final var expectedToolDefinitions = new ArrayList<>(TOOL_DEFINITIONS);
        expectedToolDefinitions.addAll(GATEWAY_A_TOOL_DEFINITIONS);
        expectedToolDefinitions.addAll(GATEWAY_B_TOOL_DEFINITIONS);

        assertThat(result.agentContext())
            .usingRecursiveComparison()
            .ignoringFields("toolDefinitions")
            .isEqualTo(AGENT_CONTEXT);
        assertThat(result.agentContext().toolDefinitions())
            .containsExactlyInAnyOrderElementsOf(expectedToolDefinitions);

        assertThat(result.remainingToolCallResults())
            .hasSize(nonDiscoveryToolCallResults.size())
            .containsExactlyElementsOf(nonDiscoveryToolCallResults);
      }
    }

    @Nested
    class TransformToolCalls {

      private static final List<ToolCall> GATEWAY_TOOL_CALLS =
          List.of(
              ToolCall.builder()
                  .id("GatewayA1_AFirstTool_123456")
                  .name("GatewayA1_AFirstTool")
                  .arguments(Map.of("arg1", "value1"))
                  .build(),
              ToolCall.builder()
                  .id("GatewayA2_ASecondTool_123456")
                  .name("GatewayA2_ASecondTool")
                  .arguments(Map.of("arg2", "value2"))
                  .build(),
              ToolCall.builder()
                  .id("GatewayB_BFirstTool_123456")
                  .name("GatewayB_BFirstTool")
                  .arguments(Map.of("arg3", "value3"))
                  .build());

      private static final List<ToolCall> EXPECTED_TRANSFORMED_GATEWAY_TOOL_CALLS =
          List.of(
              ToolCall.builder()
                  .id("GatewayA1_AFirstTool_123456")
                  .name("GatewayA1")
                  .arguments(
                      Map.ofEntries(
                          Map.entry("toolName", "AFirstTool"),
                          Map.entry("arguments", Map.of("arg1", "value1"))))
                  .build(),
              ToolCall.builder()
                  .id("GatewayA2_ASecondTool_123456")
                  .name("GatewayA2")
                  .arguments(
                      Map.ofEntries(
                          Map.entry("toolName", "ASecondTool"),
                          Map.entry("arguments", Map.of("arg2", "value2"))))
                  .build(),
              ToolCall.builder()
                  .id("GatewayB_BFirstTool_123456")
                  .name("GatewayB")
                  .arguments(
                      // payload is handler specific - in this case we assume handler B maps to an
                      // operation property
                      Map.ofEntries(
                          Map.entry("operation", "BFirstTool"),
                          Map.entry("params", Map.of("arg3", "value3"))))
                  .build());

      @Test
      void transformToolCallsPassesThroughAllHandlers_withoutNonGatewayToolCalls() {
        testTransformToolCalls(List.of());
      }

      @Test
      void transformToolCallsPassesThroughAllHandlers_keepsNonGatewayToolCallsUnmodified() {
        testTransformToolCalls(TOOL_CALLS);
      }

      private void testTransformToolCalls(List<ToolCall> nonGatewayToolCalls) {
        when(handlerA.transformToolCalls(eq(AGENT_CONTEXT), anyList()))
            .thenAnswer(
                i -> {
                  List<ToolCall> tc = i.getArgument(1);
                  return tc.stream()
                      .map(
                          toolCall -> {
                            if (toolCall.id().startsWith("GatewayA")) {
                              return ToolCall.builder()
                                  .id(toolCall.id())
                                  .name(toolCall.name().split("_")[0]) // extract gateway name
                                  .arguments(
                                      Map.ofEntries(
                                          Map.entry("toolName", toolCall.name().split("_")[1]),
                                          Map.entry("arguments", toolCall.arguments())))
                                  .build();
                            } else {
                              return toolCall;
                            }
                          })
                      .toList();
                });

        when(handlerB.transformToolCalls(eq(AGENT_CONTEXT), anyList()))
            .thenAnswer(
                i -> {
                  List<ToolCall> tc = i.getArgument(1);
                  return tc.stream()
                      .map(
                          toolCall -> {
                            if (toolCall.id().startsWith("GatewayB")) {
                              return ToolCall.builder()
                                  .id(toolCall.id())
                                  .name(toolCall.name().split("_")[0]) // extract gateway name
                                  .arguments(
                                      Map.ofEntries(
                                          Map.entry("operation", toolCall.name().split("_")[1]),
                                          Map.entry("params", toolCall.arguments())))
                                  .build();
                            } else {
                              return toolCall;
                            }
                          })
                      .toList();
                });

        final var toolCalls = new ArrayList<>(GATEWAY_TOOL_CALLS);
        toolCalls.addAll(nonGatewayToolCalls);

        final var transformed = registry.transformToolCalls(AGENT_CONTEXT, toolCalls);

        final var expectedToolCalls = new ArrayList<>(EXPECTED_TRANSFORMED_GATEWAY_TOOL_CALLS);
        expectedToolCalls.addAll(nonGatewayToolCalls);

        assertThat(transformed).containsExactlyInAnyOrderElementsOf(expectedToolCalls);
      }
    }

    @Nested
    class TransformToolCallResults {

      private static final List<ToolCallResult> GATEWAY_TOOL_CALL_RESULTS =
          List.of(
              ToolCallResult.builder()
                  .id("GatewayA1_AFirstTool_123456")
                  .name("GatewayA1")
                  .content(Map.of("result1", "value1"))
                  .build(),
              ToolCallResult.builder()
                  .id("GatewayA2_ASecondTool_123456")
                  .name("GatewayA2")
                  .content(Map.of("result2", "value2"))
                  .build(),
              ToolCallResult.builder()
                  .id("GatewayB_BFirstTool_123456")
                  .name("GatewayB")
                  .content(Map.of("result3", "value3"))
                  .build());

      private static final List<ToolCallResult> EXPECTED_TRANSFORMED_GATEWAY_TOOL_CALL_RESULTS =
          List.of(
              ToolCallResult.builder()
                  .id("GatewayA1_AFirstTool_123456")
                  .name("GatewayA1_AFirstTool")
                  .content(Map.of("result1", "value1"))
                  .build(),
              ToolCallResult.builder()
                  .id("GatewayA2_ASecondTool_123456")
                  .name("GatewayA2_ASecondTool")
                  .content(Map.of("result2", "value2"))
                  .build(),
              ToolCallResult.builder()
                  .id("GatewayB_BFirstTool_123456")
                  .name("GatewayB_BFirstTool")
                  .content(Map.of("contentB", Map.of("result3", "value3")))
                  .build());

      @Test
      void transformToolCallResultsPassesThroughAllHandlers_withoutNonGatewayToolCallResults() {
        testTransformToolCallResults(List.of());
      }

      @Test
      void
          transformToolCallResultsPassesThroughAllHandlers_keepsNonGatewayToolCallResultsUnmodified() {
        testTransformToolCallResults(TOOL_CALL_RESULTS);
      }

      private void testTransformToolCallResults(List<ToolCallResult> nonGatewayToolCallResults) {
        when(handlerA.transformToolCallResults(eq(AGENT_CONTEXT), anyList()))
            .thenAnswer(
                i -> {
                  List<ToolCallResult> tc = i.getArgument(1);
                  return tc.stream()
                      .map(
                          toolCallResult -> {
                            if (toolCallResult.id().startsWith("GatewayA")) {
                              return ToolCallResult.builder()
                                  .id(toolCallResult.id())
                                  .name(toolCallResult.id().replace("_123456", ""))
                                  .content(toolCallResult.content())
                                  .build();
                            } else {
                              return toolCallResult;
                            }
                          })
                      .toList();
                });

        when(handlerB.transformToolCallResults(eq(AGENT_CONTEXT), anyList()))
            .thenAnswer(
                i -> {
                  List<ToolCallResult> tc = i.getArgument(1);
                  return tc.stream()
                      .map(
                          toolCallResult -> {
                            if (toolCallResult.id().startsWith("GatewayB")) {
                              return ToolCallResult.builder()
                                  .id(toolCallResult.id())
                                  .name(toolCallResult.id().replace("_123456", ""))
                                  .content(Map.of("contentB", toolCallResult.content()))
                                  .build();
                            } else {
                              return toolCallResult;
                            }
                          })
                      .toList();
                });

        final var toolCallResults = new ArrayList<>(GATEWAY_TOOL_CALL_RESULTS);
        toolCallResults.addAll(nonGatewayToolCallResults);

        final var transformed = registry.transformToolCallResults(AGENT_CONTEXT, toolCallResults);

        final var expectedToolCallResults =
            new ArrayList<>(EXPECTED_TRANSFORMED_GATEWAY_TOOL_CALL_RESULTS);
        expectedToolCallResults.addAll(nonGatewayToolCallResults);

        assertThat(transformed).containsExactlyInAnyOrderElementsOf(expectedToolCallResults);
      }
    }
  }

  @Nested
  class ResolveUpdatedGatewayToolDefinitions {

    private static final AgentContext AGENT_CONTEXT = AgentContext.empty();

    @Mock private GatewayToolHandler handler1;
    @Mock private GatewayToolHandler handler2;

    private GatewayToolHandlerRegistry registry;

    @BeforeEach
    void setUp() {
      when(handler1.type()).thenReturn("type1");
      when(handler2.type()).thenReturn("type2");
      registry = new GatewayToolHandlerRegistryImpl(List.of(handler1, handler2));
    }

    @Test
    void returnsEmptyMap_whenNoChangesDetected() {
      var gatewayToolDefinitions =
          List.of(
              GatewayToolDefinition.builder()
                  .type("type1")
                  .name("gateway1")
                  .description("Gateway 1")
                  .build());

      when(handler1.resolveUpdatedGatewayToolDefinitions(AGENT_CONTEXT, gatewayToolDefinitions))
          .thenReturn(GatewayToolDefinitionUpdates.empty());
      when(handler2.resolveUpdatedGatewayToolDefinitions(AGENT_CONTEXT, gatewayToolDefinitions))
          .thenReturn(GatewayToolDefinitionUpdates.empty());

      var result =
          registry.resolveUpdatedGatewayToolDefinitions(AGENT_CONTEXT, gatewayToolDefinitions);

      assertThat(result).isEmpty();
    }

    @Test
    void returnsUpdatesFromSingleHandler() {
      var gatewayToolDefinitions =
          List.of(
              GatewayToolDefinition.builder()
                  .type("type1")
                  .name("gateway1")
                  .description("Gateway 1")
                  .build());

      final var handler1Updates = new GatewayToolDefinitionUpdates(List.of("gateway1"), List.of());
      when(handler1.resolveUpdatedGatewayToolDefinitions(AGENT_CONTEXT, gatewayToolDefinitions))
          .thenReturn(handler1Updates);
      when(handler2.resolveUpdatedGatewayToolDefinitions(AGENT_CONTEXT, gatewayToolDefinitions))
          .thenReturn(GatewayToolDefinitionUpdates.empty());

      var result =
          registry.resolveUpdatedGatewayToolDefinitions(AGENT_CONTEXT, gatewayToolDefinitions);

      assertThat(result).hasSize(1);
      assertThat(result).containsEntry("type1", handler1Updates);
    }

    @Test
    void aggregatesUpdatesFromMultipleHandlers() {
      var gatewayToolDefinitions =
          List.of(
              GatewayToolDefinition.builder()
                  .type("type1")
                  .name("gateway1")
                  .description("Gateway 1")
                  .build(),
              GatewayToolDefinition.builder()
                  .type("type2")
                  .name("gateway2")
                  .description("Gateway 2")
                  .build());

      final var handler1Updates = new GatewayToolDefinitionUpdates(List.of("gateway1"), List.of());
      final var handler2Updates =
          new GatewayToolDefinitionUpdates(List.of(), List.of("gateway-removed"));
      when(handler1.resolveUpdatedGatewayToolDefinitions(AGENT_CONTEXT, gatewayToolDefinitions))
          .thenReturn(handler1Updates);
      when(handler2.resolveUpdatedGatewayToolDefinitions(AGENT_CONTEXT, gatewayToolDefinitions))
          .thenReturn(handler2Updates);

      var result =
          registry.resolveUpdatedGatewayToolDefinitions(AGENT_CONTEXT, gatewayToolDefinitions);

      assertThat(result).hasSize(2);
      assertThat(result).containsEntry("type1", handler1Updates);
      assertThat(result).containsEntry("type2", handler2Updates);
    }

    @Test
    void excludesHandlersWithNoChanges() {
      var gatewayToolDefinitions =
          List.of(
              GatewayToolDefinition.builder()
                  .type("type1")
                  .name("gateway1")
                  .description("Gateway 1")
                  .build());

      final var handler1Updates = new GatewayToolDefinitionUpdates(List.of("gateway1"), List.of());
      when(handler1.resolveUpdatedGatewayToolDefinitions(AGENT_CONTEXT, gatewayToolDefinitions))
          .thenReturn(handler1Updates);
      when(handler2.resolveUpdatedGatewayToolDefinitions(AGENT_CONTEXT, gatewayToolDefinitions))
          .thenReturn(GatewayToolDefinitionUpdates.empty());

      var result =
          registry.resolveUpdatedGatewayToolDefinitions(AGENT_CONTEXT, gatewayToolDefinitions);

      assertThat(result).hasSize(1);
      assertThat(result).containsOnlyKeys("type1");
    }
  }
}
