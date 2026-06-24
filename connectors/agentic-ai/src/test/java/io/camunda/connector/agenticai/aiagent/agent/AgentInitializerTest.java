/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TOOL_CALL_RESULTS;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TOOL_DEFINITIONS;
import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_AGENT_INSTANCE_CREATION_FAILED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolsSchemaResponse;
import io.camunda.connector.agenticai.aiagent.agent.AgentInitializationResult.DeferConversation;
import io.camunda.connector.agenticai.aiagent.agent.AgentInitializationResult.DiscoverTools;
import io.camunda.connector.agenticai.aiagent.agent.AgentInitializationResult.ReadyToConverse;
import io.camunda.connector.agenticai.aiagent.agentinstance.AgentInstanceClient;
import io.camunda.connector.agenticai.aiagent.agentinstance.AgentInstanceKey;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentMetadata;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.request.SandboxConfiguration.DaytonaSandboxConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.SandboxConfiguration.DaytonaSandboxConfiguration.DaytonaConnection;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolDiscoveryInitiationResult;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolDiscoveryResult;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.model.tool.GatewayToolDefinition;
import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.sandbox.internaltool.InternalToolNames;
import io.camunda.connector.agenticai.sandbox.internaltool.InternalToolRegistry;
import io.camunda.connector.agenticai.sandbox.skill.SkillResolver;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.JobContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentInitializerTest {

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

  @Mock private AgentToolsResolver toolsResolver;
  @Mock private GatewayToolHandlerRegistry gatewayToolHandlers;
  @Mock private AgentInstanceClient agentInstanceClient;
  @Mock private InternalToolRegistry internalToolRegistry;
  @Mock private SkillResolver skillResolver;
  @Mock private JobContext jobContext;
  @InjectMocks private AgentInitializerImpl agentInitializer;

  @Mock private AgentExecutionContext executionContext;
  @Mock private AgentConfiguration agentConfiguration;

  @BeforeEach
  void setUpConfiguration() {
    // Default: no sandbox configured. Tests that want a sandbox must override this.
    lenient().when(executionContext.configuration()).thenReturn(agentConfiguration);
    lenient().when(agentConfiguration.sandboxConfiguration()).thenReturn(Optional.empty());
    // Default: internalToolRegistry returns empty definitions list.
    lenient().when(internalToolRegistry.toolDefinitions(anyList())).thenReturn(List.of());
  }

  @Nested
  class WithAlreadyInitializedState {

    private static final Long PROCESS_DEFINITION_KEY = 123456789L;
    private static final Long PROCESS_INSTANCE_KEY = 987654321L;
    private static final AgentMetadata EXECUTION_METADATA =
        new AgentMetadata(PROCESS_DEFINITION_KEY, PROCESS_INSTANCE_KEY, null);

    @BeforeEach
    void setUp() {
      mockJobContextMetadata(PROCESS_DEFINITION_KEY, PROCESS_INSTANCE_KEY);
    }

    @ParameterizedTest
    @EnumSource(
        value = AgentState.class,
        names = {"INITIALIZING", "TOOL_DISCOVERY"},
        mode = EnumSource.Mode.EXCLUDE)
    void shouldReturnAgentContextAndToolCallResultsFromRequest(AgentState agentState) {
      final var agentContext =
          AgentContext.empty()
              .withState(agentState)
              .withProperty("hello", "world")
              .withMetadata(EXECUTION_METADATA);
      when(executionContext.initialAgentContext()).thenReturn(agentContext);
      when(executionContext.initialToolCallResults()).thenReturn(TOOL_CALL_RESULTS);

      final var result = agentInitializer.initializeAgent(executionContext);

      assertThat(result)
          .isInstanceOfSatisfying(
              ReadyToConverse.class,
              res -> {
                assertThat(res.agentContext()).usingRecursiveComparison().isEqualTo(agentContext);
                assertThat(res.toolCallResults()).isEqualTo(TOOL_CALL_RESULTS);
              });

      verifyNoInteractions(toolsResolver, gatewayToolHandlers);
    }

    @Test
    void shouldHandleNullInitialAgentContext() {
      // When initialAgentContext is null, creates new context with INITIALIZING state
      // which triggers agent instance creation then initiateToolDiscovery flow
      when(agentInstanceClient.create(any())).thenReturn(AgentInstanceKey.of(12345L));

      when(toolsResolver.loadAdHocToolsSchema(
              any(AgentExecutionContext.class), any(AgentContext.class)))
          .thenReturn(new AdHocToolsSchemaResponse(List.of(), null));

      final var result = agentInitializer.initializeAgent(executionContext);

      final var expectedMetadata =
          new AgentMetadata(PROCESS_DEFINITION_KEY, PROCESS_INSTANCE_KEY, 12345L);
      assertThat(result)
          .isInstanceOfSatisfying(
              ReadyToConverse.class,
              res -> {
                assertThat(res.agentContext())
                    .usingRecursiveComparison()
                    .isEqualTo(
                        AgentContext.empty()
                            .withState(AgentState.READY)
                            .withMetadata(expectedMetadata));
                assertThat(res.toolCallResults()).isEmpty();
              });

      verifyNoInteractions(gatewayToolHandlers);
    }

    @Test
    void shouldHandleNullInitialToolCallResults() {
      final var agentContext =
          AgentContext.empty().withState(AgentState.READY).withMetadata(EXECUTION_METADATA);
      when(executionContext.initialAgentContext()).thenReturn(agentContext);

      final var result = agentInitializer.initializeAgent(executionContext);

      assertThat(result)
          .isInstanceOfSatisfying(
              ReadyToConverse.class,
              res -> {
                assertThat(res.agentContext()).isEqualTo(agentContext);
                assertThat(res.toolCallResults()).isEmpty();
              });

      verifyNoInteractions(toolsResolver, gatewayToolHandlers);
    }
  }

  @Nested
  class ToolDiscoveryInitiation {

    private static final AgentContext AGENT_CONTEXT =
        AgentContext.empty()
            .withProperty("hello", "world")
            .withMetadata(new AgentMetadata(123456789L, 987654321L, 99999L));

    @BeforeEach
    void setUp() {
      when(executionContext.initialAgentContext()).thenReturn(AGENT_CONTEXT);
    }

    @Test
    void shouldNotInitiateToolDiscoveryWhenNoToolElementsExist() {
      when(toolsResolver.loadAdHocToolsSchema(
              any(AgentExecutionContext.class), any(AgentContext.class)))
          .thenReturn(new AdHocToolsSchemaResponse(List.of(), null));

      final var result = agentInitializer.initializeAgent(executionContext);

      assertThat(result)
          .isInstanceOfSatisfying(
              ReadyToConverse.class,
              res -> {
                assertThat(res.agentContext())
                    .usingRecursiveComparison()
                    .isEqualTo(AGENT_CONTEXT.withState(AgentState.READY));
                assertThat(res.toolCallResults()).isEmpty();
              });

      verifyNoInteractions(gatewayToolHandlers);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void shouldReturnUpdatedAgentContextWithToolDefinitionsWhenNoGatewayToolsResolved(
        List<GatewayToolDefinition> gatewayToolDefinitions) {
      when(toolsResolver.loadAdHocToolsSchema(
              any(AgentExecutionContext.class), any(AgentContext.class)))
          .thenReturn(new AdHocToolsSchemaResponse(TOOL_DEFINITIONS, gatewayToolDefinitions));

      final var result = agentInitializer.initializeAgent(executionContext);

      assertThat(result)
          .isInstanceOfSatisfying(
              ReadyToConverse.class,
              res -> {
                assertThat(res.agentContext())
                    .usingRecursiveComparison()
                    .isEqualTo(
                        AGENT_CONTEXT
                            .withState(AgentState.READY)
                            .withToolDefinitions(TOOL_DEFINITIONS));
                assertThat(res.toolCallResults()).isEmpty();
              });

      verifyNoInteractions(gatewayToolHandlers);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void shouldReturnUpdatedAgentContextWithToolDefinitionsWhenNoToolDiscoveryCallsReturned(
        List<ToolCall> toolDiscoveryToolCalls) {
      when(toolsResolver.loadAdHocToolsSchema(
              any(AgentExecutionContext.class), any(AgentContext.class)))
          .thenReturn(new AdHocToolsSchemaResponse(TOOL_DEFINITIONS, GATEWAY_TOOL_DEFINITIONS));

      when(gatewayToolHandlers.initiateToolDiscovery(
              any(AgentContext.class), eq(GATEWAY_TOOL_DEFINITIONS)))
          .thenAnswer(
              args ->
                  new GatewayToolDiscoveryInitiationResult(
                      args.getArgument(0, AgentContext.class)
                          .withProperty("mcpClients", List.of("AnMcpClient")),
                      toolDiscoveryToolCalls));

      final var result = agentInitializer.initializeAgent(executionContext);

      assertThat(result)
          .isInstanceOfSatisfying(
              ReadyToConverse.class,
              res -> {
                assertThat(res.agentContext())
                    .usingRecursiveComparison()
                    .isEqualTo(
                        AGENT_CONTEXT
                            .withState(AgentState.READY)
                            .withToolDefinitions(TOOL_DEFINITIONS)
                            .withProperty("mcpClients", List.of("AnMcpClient")));
                assertThat(res.toolCallResults()).isEmpty();
              });
    }

    @Test
    void shouldReturnDiscoverToolsWithDiscoveryToolCallsWhenToolDiscoveryCallsAreReturned() {
      final var toolDiscoveryToolCalls =
          List.of(
              ToolCall.builder()
                  .id("MCP_toolsList__AnMcpClient")
                  .name("AnMcpClient")
                  .arguments(Map.of("method", "tools/list"))
                  .build());

      when(toolsResolver.loadAdHocToolsSchema(
              any(AgentExecutionContext.class), any(AgentContext.class)))
          .thenReturn(new AdHocToolsSchemaResponse(TOOL_DEFINITIONS, GATEWAY_TOOL_DEFINITIONS));

      when(gatewayToolHandlers.initiateToolDiscovery(
              any(AgentContext.class), eq(GATEWAY_TOOL_DEFINITIONS)))
          .thenAnswer(
              args ->
                  new GatewayToolDiscoveryInitiationResult(
                      args.getArgument(0, AgentContext.class)
                          .withProperty("mcpClients", List.of("AnMcpClient")),
                      toolDiscoveryToolCalls));

      final var result = agentInitializer.initializeAgent(executionContext);

      assertThat(result)
          .isInstanceOfSatisfying(
              DiscoverTools.class,
              res -> {
                assertThat(res.agentContext())
                    .usingRecursiveComparison()
                    .isEqualTo(
                        AGENT_CONTEXT
                            .withState(AgentState.TOOL_DISCOVERY)
                            .withToolDefinitions(TOOL_DEFINITIONS)
                            .withProperty("mcpClients", List.of("AnMcpClient")));
                assertThat(res.toolDiscoveryToolCalls())
                    .containsExactlyElementsOf(toolDiscoveryToolCalls);
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

    @BeforeEach
    void setUp() {
      when(executionContext.initialAgentContext()).thenReturn(AGENT_CONTEXT);
    }

    @Test
    void shouldHandleToolDiscoveryResults() {
      when(executionContext.initialToolCallResults())
          .thenReturn(GATEWAY_TOOL_DISCOVERY_TOOL_CALL_RESULTS);

      final var expectedToolDefinitions = new ArrayList<>(TOOL_DEFINITIONS);
      expectedToolDefinitions.addAll(RESOLVED_GATEWAY_TOOL_DEFINITIONS);

      when(gatewayToolHandlers.allToolDiscoveryResultsPresent(
              any(AgentContext.class), eq(GATEWAY_TOOL_DISCOVERY_TOOL_CALL_RESULTS)))
          .thenReturn(true);

      when(gatewayToolHandlers.handleToolDiscoveryResults(
              any(AgentContext.class), eq(GATEWAY_TOOL_DISCOVERY_TOOL_CALL_RESULTS)))
          .thenAnswer(
              args ->
                  new GatewayToolDiscoveryResult(
                      args.getArgument(0, AgentContext.class)
                          .withToolDefinitions(expectedToolDefinitions)
                          .withProperty("discovered", true),
                      List.of()));

      final var result = agentInitializer.initializeAgent(executionContext);

      assertThat(result)
          .isInstanceOfSatisfying(
              ReadyToConverse.class,
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
                assertThat(res.toolCallResults()).isEmpty();
              });
    }

    @Test
    void shouldReturnPotentialRemainingNonDiscoveryToolCallResults() {
      final var expectedToolDefinitions = new ArrayList<>(TOOL_DEFINITIONS);
      expectedToolDefinitions.addAll(RESOLVED_GATEWAY_TOOL_DEFINITIONS);

      final var mergedToolCallResults = new ArrayList<>(TOOL_CALL_RESULTS);
      mergedToolCallResults.addAll(GATEWAY_TOOL_DISCOVERY_TOOL_CALL_RESULTS);

      when(executionContext.initialToolCallResults()).thenReturn(mergedToolCallResults);

      when(gatewayToolHandlers.allToolDiscoveryResultsPresent(
              any(AgentContext.class), eq(mergedToolCallResults)))
          .thenReturn(true);

      when(gatewayToolHandlers.handleToolDiscoveryResults(
              any(AgentContext.class), eq(mergedToolCallResults)))
          .thenAnswer(
              args ->
                  new GatewayToolDiscoveryResult(
                      args.getArgument(0, AgentContext.class)
                          .withToolDefinitions(expectedToolDefinitions)
                          .withProperty("discovered", true),
                      TOOL_CALL_RESULTS));

      final var result = agentInitializer.initializeAgent(executionContext);

      assertThat(result)
          .isInstanceOfSatisfying(
              ReadyToConverse.class,
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

    @Test
    void shouldReturnDiscoveryInProgressWhenNotAllToolDiscoveryResultsPresent() {
      when(executionContext.initialToolCallResults())
          .thenReturn(GATEWAY_TOOL_DISCOVERY_TOOL_CALL_RESULTS);

      when(gatewayToolHandlers.allToolDiscoveryResultsPresent(
              any(AgentContext.class), eq(GATEWAY_TOOL_DISCOVERY_TOOL_CALL_RESULTS)))
          .thenReturn(false);

      final var result = agentInitializer.initializeAgent(executionContext);

      assertThat(result).isInstanceOf(DeferConversation.class);
    }

    @Test
    void shouldReturnDiscoveryInProgressWithPartialResults() {
      final var partialToolCallResults =
          List.of(
              ToolCallResult.builder()
                  .id("MCP_toolsList__AnMcpClient")
                  .name("AnMcpClient")
                  .content(Map.of("toolDefinitions", RESOLVED_GATEWAY_TOOL_DEFINITIONS))
                  .build());

      when(executionContext.initialToolCallResults()).thenReturn(partialToolCallResults);

      when(gatewayToolHandlers.allToolDiscoveryResultsPresent(
              any(AgentContext.class), eq(partialToolCallResults)))
          .thenReturn(false);

      final var result = agentInitializer.initializeAgent(executionContext);

      assertThat(result).isInstanceOf(DeferConversation.class);
    }
  }

  @Nested
  class ProcessMigration {

    private static final long ORIGINAL_PROCESS_DEFINITION_KEY = 111111111L;
    private static final long MIGRATED_PROCESS_DEFINITION_KEY = 222222222L;
    private static final long PROCESS_INSTANCE_KEY = 987654321L;

    @Test
    void shouldTriggerToolUpdateWhenMetadataIsNull() {
      final var agentContext =
          AgentContext.empty().withState(AgentState.READY).withToolDefinitions(TOOL_DEFINITIONS);
      when(executionContext.initialAgentContext()).thenReturn(agentContext);

      mockJobContextMetadata(MIGRATED_PROCESS_DEFINITION_KEY, PROCESS_INSTANCE_KEY);

      final var updatedAgentContext =
          agentContext.withToolDefinitions(TOOL_DEFINITIONS).withProperty("updated", true);
      when(toolsResolver.updateToolDefinitions(executionContext, agentContext))
          .thenReturn(updatedAgentContext);

      final var result = agentInitializer.initializeAgent(executionContext);

      final var expectedMetadata =
          new AgentMetadata(MIGRATED_PROCESS_DEFINITION_KEY, PROCESS_INSTANCE_KEY, null);
      assertThat(result)
          .isInstanceOfSatisfying(
              ReadyToConverse.class,
              res -> {
                assertThat(res.agentContext().metadata()).isEqualTo(expectedMetadata);
                assertThat(res.agentContext().properties()).containsEntry("updated", true);
              });
    }

    @Test
    void shouldTriggerToolUpdateWhenProcessDefinitionKeyChanged() {
      final var originalMetadata =
          new AgentMetadata(ORIGINAL_PROCESS_DEFINITION_KEY, PROCESS_INSTANCE_KEY, null);
      final var agentContext =
          AgentContext.empty()
              .withState(AgentState.READY)
              .withMetadata(originalMetadata)
              .withToolDefinitions(TOOL_DEFINITIONS);
      when(executionContext.initialAgentContext()).thenReturn(agentContext);

      mockJobContextMetadata(MIGRATED_PROCESS_DEFINITION_KEY, PROCESS_INSTANCE_KEY);

      final var updatedAgentContext =
          agentContext.withToolDefinitions(TOOL_DEFINITIONS).withProperty("migrated", true);
      when(toolsResolver.updateToolDefinitions(executionContext, agentContext))
          .thenReturn(updatedAgentContext);

      final var result = agentInitializer.initializeAgent(executionContext);

      final var expectedMetadata =
          new AgentMetadata(MIGRATED_PROCESS_DEFINITION_KEY, PROCESS_INSTANCE_KEY, null);
      assertThat(result)
          .isInstanceOfSatisfying(
              ReadyToConverse.class,
              res -> {
                assertThat(res.agentContext().metadata()).isEqualTo(expectedMetadata);
                assertThat(res.agentContext().properties()).containsEntry("migrated", true);
              });
    }

    @Test
    void shouldSkipToolUpdateWhenProcessDefinitionKeyMatches() {
      final var metadata =
          new AgentMetadata(ORIGINAL_PROCESS_DEFINITION_KEY, PROCESS_INSTANCE_KEY, null);
      final var agentContext =
          AgentContext.empty()
              .withState(AgentState.READY)
              .withMetadata(metadata)
              .withToolDefinitions(TOOL_DEFINITIONS);
      when(executionContext.initialAgentContext()).thenReturn(agentContext);
      when(executionContext.initialToolCallResults()).thenReturn(TOOL_CALL_RESULTS);

      mockJobContextMetadata(ORIGINAL_PROCESS_DEFINITION_KEY, PROCESS_INSTANCE_KEY);

      final var result = agentInitializer.initializeAgent(executionContext);

      assertThat(result)
          .isInstanceOfSatisfying(
              ReadyToConverse.class,
              res -> {
                assertThat(res.agentContext()).isEqualTo(agentContext);
                assertThat(res.toolCallResults()).isEqualTo(TOOL_CALL_RESULTS);
              });

      verifyNoInteractions(toolsResolver, gatewayToolHandlers);
    }
  }

  @Nested
  class AgentInstanceCreation {

    private static final long PROCESS_DEFINITION_KEY = 100L;
    private static final long PROCESS_INSTANCE_KEY = 200L;

    @BeforeEach
    void setUp() {
      lenient().when(executionContext.jobContext()).thenReturn(jobContext);
      lenient().when(jobContext.getProcessDefinitionKey()).thenReturn(PROCESS_DEFINITION_KEY);
      lenient().when(jobContext.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldCreateAgentInstanceOnFirstInitialization() {
      // null initialAgentContext → creates INITIALIZING context without agentInstanceKey
      when(agentInstanceClient.create(any(AgentExecutionContext.class)))
          .thenReturn(AgentInstanceKey.of(12345L));
      when(toolsResolver.loadAdHocToolsSchema(
              any(AgentExecutionContext.class), any(AgentContext.class)))
          .thenReturn(new AdHocToolsSchemaResponse(List.of(), null));

      final var result = (ReadyToConverse) agentInitializer.initializeAgent(executionContext);

      verify(agentInstanceClient, times(1)).create(any(AgentExecutionContext.class));
      assertThat(result.agentContext().metadata().agentInstanceKey()).isEqualTo(12345L);
    }

    @Test
    void shouldSkipAgentInstanceCreationWhenKeyAlreadyPresent() {
      final var existingMetadata =
          new AgentMetadata(PROCESS_DEFINITION_KEY, PROCESS_INSTANCE_KEY, 99L);
      final var agentContext =
          AgentContext.empty().withState(AgentState.INITIALIZING).withMetadata(existingMetadata);
      when(executionContext.initialAgentContext()).thenReturn(agentContext);
      when(toolsResolver.loadAdHocToolsSchema(
              any(AgentExecutionContext.class), any(AgentContext.class)))
          .thenReturn(new AdHocToolsSchemaResponse(List.of(), null));

      agentInitializer.initializeAgent(executionContext);

      verify(agentInstanceClient, never()).create(any());
    }

    @Test
    void shouldSkipAgentInstanceCreationOnMissingAgentInstanceKeyInExistingAgentContext() {
      final var existingMetadata =
          new AgentMetadata(PROCESS_DEFINITION_KEY, PROCESS_INSTANCE_KEY, null);
      // one step ahead of the initialization state
      final var agentContext =
          AgentContext.empty().withState(AgentState.TOOL_DISCOVERY).withMetadata(existingMetadata);
      when(executionContext.initialAgentContext()).thenReturn(agentContext);

      agentInitializer.initializeAgent(executionContext);

      verify(agentInstanceClient, never()).create(any());
    }

    @Test
    void shouldPropagateConnectorExceptionWhenAgentInstanceCreationFails() {
      final var failure =
          new ConnectorException(
              ERROR_CODE_AGENT_INSTANCE_CREATION_FAILED, "Failed to create agent instance");
      when(agentInstanceClient.create(any(AgentExecutionContext.class))).thenThrow(failure);

      assertThatThrownBy(() -> agentInitializer.initializeAgent(executionContext))
          .isInstanceOf(ConnectorException.class)
          .satisfies(
              e ->
                  assertThat(((ConnectorException) e).getErrorCode())
                      .isEqualTo(ERROR_CODE_AGENT_INSTANCE_CREATION_FAILED));
    }
  }

  // ---------------------------------------------------------------------------
  // Sandbox / internal-tool registration acceptance criteria (T4)
  // ---------------------------------------------------------------------------

  @Nested
  class SandboxToolRegistration {

    private static final AgentContext AGENT_CONTEXT =
        AgentContext.empty()
            .withProperty("hello", "world")
            .withMetadata(new AgentMetadata(123456789L, 987654321L, 99999L));

    private static final ToolDefinition ADHOC_TOOL =
        ToolDefinition.builder().name("myAdHocTool").description("An ad-hoc tool").build();

    private static final ToolDefinition BASH_DEF =
        ToolDefinition.builder().name(InternalToolNames.BASH).description("Run bash").build();

    private static final ToolDefinition FS_READ_DEF =
        ToolDefinition.builder().name(InternalToolNames.FS_READ).description("Read file").build();

    private static final ToolDefinition FS_WRITE_DEF =
        ToolDefinition.builder().name(InternalToolNames.FS_WRITE).description("Write file").build();

    @BeforeEach
    void setUp() {
      when(executionContext.initialAgentContext()).thenReturn(AGENT_CONTEXT);
    }

    /**
     * AC: No sandbox config → internal tools are NOT added; tool definitions equal the ad-hoc tools
     * exactly (byte-for-byte unchanged).
     */
    @Test
    void withoutSandbox_toolDefinitionsEqualAdHocToolsOnly() {
      // No sandbox configured — default stub from outer @BeforeEach returns Optional.empty()
      when(toolsResolver.loadAdHocToolsSchema(any(), any()))
          .thenReturn(new AdHocToolsSchemaResponse(List.of(ADHOC_TOOL), null));

      final var result = (ReadyToConverse) agentInitializer.initializeAgent(executionContext);

      assertThat(result.agentContext().toolDefinitions())
          .containsExactly(ADHOC_TOOL)
          .doesNotContain(BASH_DEF, FS_READ_DEF, FS_WRITE_DEF);
    }

    /**
     * AC: Sandbox present → bash/fs_read/fs_write definitions appear in agent context tool
     * definitions, appended after the ad-hoc tools.
     */
    @Test
    void withSandbox_internalToolDefinitionsAreAppendedAfterAdHocTools() {
      when(agentConfiguration.sandboxConfiguration())
          .thenReturn(
              Optional.of(
                  new DaytonaSandboxConfiguration(
                      new DaytonaConnection("key", null, null, null, null, null))));
      when(internalToolRegistry.toolDefinitions(anyList()))
          .thenReturn(List.of(BASH_DEF, FS_READ_DEF, FS_WRITE_DEF));
      when(toolsResolver.loadAdHocToolsSchema(any(), any()))
          .thenReturn(new AdHocToolsSchemaResponse(List.of(ADHOC_TOOL), null));

      final var result = (ReadyToConverse) agentInitializer.initializeAgent(executionContext);

      assertThat(result.agentContext().toolDefinitions())
          .extracting(ToolDefinition::name)
          .containsExactly(
              "myAdHocTool",
              InternalToolNames.BASH,
              InternalToolNames.FS_READ,
              InternalToolNames.FS_WRITE);
    }

    /** AC: Sandbox present, no ad-hoc tools → only internal tools appear. */
    @Test
    void withSandboxAndNoAdHocTools_onlyInternalToolDefinitionsRegistered() {
      when(agentConfiguration.sandboxConfiguration())
          .thenReturn(
              Optional.of(
                  new DaytonaSandboxConfiguration(
                      new DaytonaConnection("key", null, null, null, null, null))));
      when(internalToolRegistry.toolDefinitions(anyList()))
          .thenReturn(List.of(BASH_DEF, FS_READ_DEF, FS_WRITE_DEF));
      when(toolsResolver.loadAdHocToolsSchema(any(), any()))
          .thenReturn(new AdHocToolsSchemaResponse(List.of(), null));

      final var result = (ReadyToConverse) agentInitializer.initializeAgent(executionContext);

      assertThat(result.agentContext().toolDefinitions())
          .extracting(ToolDefinition::name)
          .containsExactlyInAnyOrder(
              InternalToolNames.BASH, InternalToolNames.FS_READ, InternalToolNames.FS_WRITE);
    }
  }

  private void mockJobContextMetadata(long processDefinitionKey, long processInstanceKey) {
    when(executionContext.jobContext()).thenReturn(jobContext);
    when(jobContext.getProcessDefinitionKey()).thenReturn(processDefinitionKey);
    when(jobContext.getProcessInstanceKey()).thenReturn(processInstanceKey);
  }
}
