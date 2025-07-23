/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.AD_HOC_TOOL_ELEMENTS;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TOOL_CALL_RESULTS;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TOOL_DEFINITIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolElement;
import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolsSchemaResponse;
import io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.ProcessDefinitionAdHocToolElementsResolver;
import io.camunda.connector.agenticai.adhoctoolsschema.schema.AdHocToolsSchemaResolver;
import io.camunda.connector.agenticai.adhoctoolsschema.schema.GatewayToolDefinitionResolver;
import io.camunda.connector.agenticai.aiagent.agent.AgentInitializationResult.AgentContextInitializationResult;
import io.camunda.connector.agenticai.aiagent.agent.AgentInitializationResult.AgentResponseInitializationResult;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.OutboundConnectorAgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.OutboundConnectorAgentJobContext;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest.AgentRequestData;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest.AgentRequestData.ToolsConfiguration;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolDiscoveryInitiationResult;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolDiscoveryResult;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.model.tool.GatewayToolDefinition;
import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallProcessVariable;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentInitializerTest {

  private static final Long PROCESS_DEFINITION_KEY = 123456789L;
  private static final String CONTAINER_ELEMENT_ID = "test-container-element-id";

  public static final List<AdHocToolElement> AD_HOC_TOOL_ELEMENTS_WITH_GATEWAY_TOOL_ELEMENTS =
      Stream.concat(
              AD_HOC_TOOL_ELEMENTS.stream(),
              Stream.of(
                  AdHocToolElement.builder()
                      .elementId("mcpClient")
                      .elementName("AnMcpClient")
                      .documentation("An MCP client for this cool service.")
                      .properties(
                          Map.of(GatewayToolDefinitionResolver.GATEWAY_TYPE_EXTENSION, "mcpClient"))
                      .build(),
                  AdHocToolElement.builder()
                      .elementId("dummyType")
                      .elementName("SomeOtherGateway")
                      .documentation("Some other gateway type.")
                      .properties(
                          Map.of(GatewayToolDefinitionResolver.GATEWAY_TYPE_EXTENSION, "dummyType"))
                      .build()))
          .toList();

  private static final List<GatewayToolDefinition> GATEWAY_TOOL_DEFINITIONS =
      List.of(
          GatewayToolDefinition.builder()
              .type("mcpClient")
              .name("AnMcpClient")
              .description("An MCP client for this cool service.")
              .properties(Map.of("dummy", 42))
              .build(),
          GatewayToolDefinition.builder()
              .type("dummyType")
              .name("SomeOtherGateway")
              .description("Some other gateway type.")
              .build());

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private OutboundConnectorAgentJobContext agentJobContext;

  @Mock private ProcessDefinitionAdHocToolElementsResolver toolElementsResolver;
  @Mock private AdHocToolsSchemaResolver toolsSchemaResolver;
  @Mock private GatewayToolHandlerRegistry gatewayToolHandlers;
  @InjectMocks private AgentInitializerImpl agentInitializer;

  @BeforeEach
  void setUp() {
    lenient().when(agentJobContext.processDefinitionKey()).thenReturn(PROCESS_DEFINITION_KEY);
  }

  @Nested
  class WithAlreadyInitializedState {

    @ParameterizedTest
    @EnumSource(
        value = AgentState.class,
        names = {"INITIALIZING", "TOOL_DISCOVERY"},
        mode = EnumSource.Mode.EXCLUDE)
    void returnsAgentContextAndToolCallResultsFromRequest(AgentState agentState) {
      final var agentContext =
          AgentContext.empty().withState(agentState).withProperty("hello", "world");
      final var result =
          agentInitializer.initializeAgent(
              executionContext(
                  agentContext, new ToolsConfiguration(CONTAINER_ELEMENT_ID, TOOL_CALL_RESULTS)));

      assertThat(result)
          .isInstanceOfSatisfying(
              AgentContextInitializationResult.class,
              res -> {
                assertThat(res.agentContext()).usingRecursiveComparison().isEqualTo(agentContext);
                assertThat(res.toolCallResults()).isEqualTo(TOOL_CALL_RESULTS);
              });

      verifyNoInteractions(toolElementsResolver, toolsSchemaResolver, gatewayToolHandlers);
    }

    @Test
    void handlesNullToolsConfiguration() {
      final var agentContext = AgentContext.empty().withState(AgentState.READY);
      final var result = agentInitializer.initializeAgent(executionContext(agentContext, null));

      assertThat(result)
          .isInstanceOfSatisfying(
              AgentContextInitializationResult.class,
              res -> {
                assertThat(res.agentContext()).usingRecursiveComparison().isEqualTo(agentContext);
                assertThat(res.toolCallResults()).isNotNull().isEmpty();
              });

      verifyNoInteractions(toolElementsResolver, toolsSchemaResolver, gatewayToolHandlers);
    }

    @Test
    void handlesNullToolCallResultsConfiguration() {
      final var agentContext = AgentContext.empty().withState(AgentState.READY);
      final var result =
          agentInitializer.initializeAgent(
              executionContext(agentContext, new ToolsConfiguration(CONTAINER_ELEMENT_ID, null)));

      assertThat(result)
          .isInstanceOfSatisfying(
              AgentContextInitializationResult.class,
              res -> {
                assertThat(res.agentContext()).isEqualTo(agentContext);
                assertThat(res.toolCallResults()).isNotNull().isEmpty();
              });

      verifyNoInteractions(toolElementsResolver, toolsSchemaResolver, gatewayToolHandlers);
    }
  }

  @Nested
  class ToolDiscoveryInitiation {

    private static final AgentContext AGENT_CONTEXT =
        AgentContext.empty().withProperty("hello", "world");

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void noToolDiscoveryWhenNoContainerElementIdIsConfigured(String containerElementId) {
      final var result =
          agentInitializer.initializeAgent(
              executionContext(
                  AGENT_CONTEXT, new ToolsConfiguration(containerElementId, List.of())));

      assertThat(result)
          .isInstanceOfSatisfying(
              AgentContextInitializationResult.class,
              res -> {
                assertThat(res.agentContext())
                    .usingRecursiveComparison()
                    .isEqualTo(AGENT_CONTEXT.withState(AgentState.READY));
                assertThat(res.toolCallResults()).isNotNull().isEmpty();
              });

      verifyNoInteractions(toolElementsResolver, toolsSchemaResolver, gatewayToolHandlers);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void
        whenNoGatewayToolsResolved_returnsUpdatedAgentContextIncludingToolDefinitionsWithoutGatewayDiscovery(
            List<GatewayToolDefinition> gatewayToolDefinitions) {
      when(toolElementsResolver.resolveToolElements(PROCESS_DEFINITION_KEY, CONTAINER_ELEMENT_ID))
          .thenReturn(AD_HOC_TOOL_ELEMENTS);
      when(toolsSchemaResolver.resolveAdHocToolsSchema(AD_HOC_TOOL_ELEMENTS))
          .thenReturn(new AdHocToolsSchemaResponse(TOOL_DEFINITIONS, gatewayToolDefinitions));

      final var result =
          agentInitializer.initializeAgent(
              executionContext(
                  AGENT_CONTEXT, new ToolsConfiguration(CONTAINER_ELEMENT_ID, List.of())));

      assertThat(result)
          .isInstanceOfSatisfying(
              AgentContextInitializationResult.class,
              res -> {
                assertThat(res.agentContext())
                    .usingRecursiveComparison()
                    .isEqualTo(
                        AGENT_CONTEXT
                            .withState(AgentState.READY)
                            .withToolDefinitions(TOOL_DEFINITIONS));
                assertThat(res.toolCallResults()).isNotNull().isEmpty();
              });

      verifyNoInteractions(gatewayToolHandlers);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void whenNoToolDiscoveryToolCallsReturned_returnsUpdatedAgentContextIncludingToolDefinitions(
        List<ToolCall> toolDiscoveryToolCalls) {
      when(toolElementsResolver.resolveToolElements(PROCESS_DEFINITION_KEY, CONTAINER_ELEMENT_ID))
          .thenReturn(AD_HOC_TOOL_ELEMENTS_WITH_GATEWAY_TOOL_ELEMENTS);
      when(toolsSchemaResolver.resolveAdHocToolsSchema(
              AD_HOC_TOOL_ELEMENTS_WITH_GATEWAY_TOOL_ELEMENTS))
          .thenReturn(new AdHocToolsSchemaResponse(TOOL_DEFINITIONS, GATEWAY_TOOL_DEFINITIONS));

      when(gatewayToolHandlers.initiateToolDiscovery(
              any(AgentContext.class), eq(GATEWAY_TOOL_DEFINITIONS)))
          .thenAnswer(
              args ->
                  new GatewayToolDiscoveryInitiationResult(
                      args.getArgument(0, AgentContext.class)
                          .withProperty("mcpClients", List.of("AnMcpClient")),
                      toolDiscoveryToolCalls));

      final var result =
          agentInitializer.initializeAgent(
              executionContext(
                  AGENT_CONTEXT, new ToolsConfiguration(CONTAINER_ELEMENT_ID, List.of())));

      assertThat(result)
          .isInstanceOfSatisfying(
              AgentContextInitializationResult.class,
              res -> {
                assertThat(res.agentContext())
                    .usingRecursiveComparison()
                    .isEqualTo(
                        AGENT_CONTEXT
                            .withState(AgentState.READY)
                            .withToolDefinitions(TOOL_DEFINITIONS)
                            .withProperty("mcpClients", List.of("AnMcpClient")));
                assertThat(res.toolCallResults()).isNotNull().isEmpty();
              });
    }

    @Test
    void whenToolDiscoveryToolCallsReturned_returnsAgentResponseIncludingDiscoveryToolCalls() {
      final var toolDiscoveryToolCalls =
          List.of(
              ToolCall.builder()
                  .id("MCP_toolsList__AnMcpClient")
                  .name("AnMcpClient")
                  .arguments(Map.of("method", "tools/list"))
                  .build());

      when(toolElementsResolver.resolveToolElements(PROCESS_DEFINITION_KEY, CONTAINER_ELEMENT_ID))
          .thenReturn(AD_HOC_TOOL_ELEMENTS_WITH_GATEWAY_TOOL_ELEMENTS);
      when(toolsSchemaResolver.resolveAdHocToolsSchema(
              AD_HOC_TOOL_ELEMENTS_WITH_GATEWAY_TOOL_ELEMENTS))
          .thenReturn(new AdHocToolsSchemaResponse(TOOL_DEFINITIONS, GATEWAY_TOOL_DEFINITIONS));

      when(gatewayToolHandlers.initiateToolDiscovery(
              any(AgentContext.class), eq(GATEWAY_TOOL_DEFINITIONS)))
          .thenAnswer(
              args ->
                  new GatewayToolDiscoveryInitiationResult(
                      args.getArgument(0, AgentContext.class)
                          .withProperty("mcpClients", List.of("AnMcpClient")),
                      toolDiscoveryToolCalls));

      final var result =
          agentInitializer.initializeAgent(
              executionContext(
                  AGENT_CONTEXT, new ToolsConfiguration(CONTAINER_ELEMENT_ID, List.of())));

      assertThat(result)
          .isInstanceOfSatisfying(
              AgentResponseInitializationResult.class,
              res -> {
                assertThat(res.agentResponse()).isNotNull();
                assertThat(res.agentResponse().context())
                    .usingRecursiveComparison()
                    .isEqualTo(
                        AGENT_CONTEXT
                            .withState(AgentState.TOOL_DISCOVERY)
                            .withToolDefinitions(TOOL_DEFINITIONS)
                            .withProperty("mcpClients", List.of("AnMcpClient")));
                assertThat(res.agentResponse().toolCalls())
                    .containsExactlyElementsOf(
                        toolDiscoveryToolCalls.stream()
                            .map(ToolCallProcessVariable::from)
                            .toList());
              });
    }
  }

  @Nested
  class ToolDiscoveryResults {

    private static final AgentContext AGENT_CONTEXT =
        AgentContext.empty()
            .withState(AgentState.TOOL_DISCOVERY)
            .withToolDefinitions(TOOL_DEFINITIONS)
            .withProperty("mcpClients", List.of("AnMcpClient"));

    private static final List<ToolDefinition> RESOLVED_GATEWAY_TOOL_DEFINITIONS =
        List.of(
            ToolDefinition.builder().name("FirstTool").description("First tool").build(),
            ToolDefinition.builder()
                .name("SecondTool")
                .description("Second tool")
                .inputSchema(Map.of("type", "object"))
                .build());

    private static final List<ToolCallResult> GATEWAY_TOOL_DISCOVERY_TOOL_CALL_RESULTS =
        List.of(
            ToolCallResult.builder()
                .id("MCP_toolsList__AnMcpClient")
                .name("AnMcpClient")
                .content(Map.of("toolDefinitions", RESOLVED_GATEWAY_TOOL_DEFINITIONS))
                .build());

    @Test
    void handlesToolDiscoveryResults() {
      final var expectedToolDefinitions = new ArrayList<>(TOOL_DEFINITIONS);
      expectedToolDefinitions.addAll(RESOLVED_GATEWAY_TOOL_DEFINITIONS);

      when(gatewayToolHandlers.handleToolDiscoveryResults(
              any(AgentContext.class), eq(GATEWAY_TOOL_DISCOVERY_TOOL_CALL_RESULTS)))
          .thenAnswer(
              args ->
                  new GatewayToolDiscoveryResult(
                      args.getArgument(0, AgentContext.class)
                          .withToolDefinitions(expectedToolDefinitions)
                          .withProperty("discovered", true),
                      List.of()));

      final var result =
          agentInitializer.initializeAgent(
              executionContext(
                  AGENT_CONTEXT,
                  new ToolsConfiguration(
                      CONTAINER_ELEMENT_ID, GATEWAY_TOOL_DISCOVERY_TOOL_CALL_RESULTS)));

      assertThat(result)
          .isInstanceOfSatisfying(
              AgentContextInitializationResult.class,
              res -> {
                assertThat(res.agentContext())
                    .usingRecursiveComparison()
                    .isEqualTo(
                        AGENT_CONTEXT
                            .withState(AgentState.READY)
                            .withToolDefinitions(expectedToolDefinitions)
                            .withProperty("mcpClients", List.of("AnMcpClient"))
                            .withProperty("discovered", true));

                // filtered out by the gateway tool handler
                assertThat(res.toolCallResults()).isNotNull().isEmpty();
              });
    }

    @Test
    void returnsPotentialRemainingNonDiscoveryToolCallResults() {
      final var expectedToolDefinitions = new ArrayList<>(TOOL_DEFINITIONS);
      expectedToolDefinitions.addAll(RESOLVED_GATEWAY_TOOL_DEFINITIONS);

      final var mergedToolCallResults = new ArrayList<>(TOOL_CALL_RESULTS);
      mergedToolCallResults.addAll(GATEWAY_TOOL_DISCOVERY_TOOL_CALL_RESULTS);

      when(gatewayToolHandlers.handleToolDiscoveryResults(
              any(AgentContext.class), eq(mergedToolCallResults)))
          .thenAnswer(
              args ->
                  new GatewayToolDiscoveryResult(
                      args.getArgument(0, AgentContext.class)
                          .withToolDefinitions(expectedToolDefinitions)
                          .withProperty("discovered", true),
                      TOOL_CALL_RESULTS));

      final var result =
          agentInitializer.initializeAgent(
              executionContext(
                  AGENT_CONTEXT,
                  new ToolsConfiguration(CONTAINER_ELEMENT_ID, mergedToolCallResults)));

      assertThat(result)
          .isInstanceOfSatisfying(
              AgentContextInitializationResult.class,
              res -> {
                assertThat(res.agentContext())
                    .usingRecursiveComparison()
                    .isEqualTo(
                        AGENT_CONTEXT
                            .withState(AgentState.READY)
                            .withToolDefinitions(expectedToolDefinitions)
                            .withProperty("mcpClients", List.of("AnMcpClient"))
                            .withProperty("discovered", true));

                assertThat(res.toolCallResults()).containsExactlyElementsOf(TOOL_CALL_RESULTS);
              });
    }
  }

  private AgentExecutionContext executionContext(
      AgentContext agentContext, ToolsConfiguration toolsConfiguration) {
    final var agentRequest =
        new AgentRequest(
            null,
            new AgentRequestData(agentContext, null, null, toolsConfiguration, null, null, null));

    return new OutboundConnectorAgentExecutionContext(agentJobContext, agentRequest);
  }
}
