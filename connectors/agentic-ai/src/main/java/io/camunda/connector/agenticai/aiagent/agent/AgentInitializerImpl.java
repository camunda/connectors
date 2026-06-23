/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.agent.AgentInitializationResult.DeferConversation;
import io.camunda.connector.agenticai.aiagent.agent.AgentInitializationResult.DiscoverTools;
import io.camunda.connector.agenticai.aiagent.agent.AgentInitializationResult.ReadyToConverse;
import io.camunda.connector.agenticai.aiagent.agentinstance.AgentInstanceClient;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentMetadata;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolDiscoveryInitiationResult;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.model.tool.GatewayToolDefinition;
import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.sandbox.internaltool.InternalToolRegistry;
import io.camunda.connector.agenticai.sandbox.skill.SkillResolver;
import io.camunda.connector.agenticai.sandbox.skill.SkillResolver.SkillMetadata;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

public class AgentInitializerImpl implements AgentInitializer {

  private static final Logger LOGGER = LoggerFactory.getLogger(AgentInitializerImpl.class);

  private final AgentToolsResolver toolsResolver;
  private final GatewayToolHandlerRegistry gatewayToolHandlers;
  private final AgentInstanceClient agentInstanceClient;
  private final InternalToolRegistry internalToolRegistry;
  private final SkillResolver skillResolver;

  public AgentInitializerImpl(
      AgentToolsResolver toolsResolver,
      GatewayToolHandlerRegistry gatewayToolHandlers,
      AgentInstanceClient agentInstanceClient,
      InternalToolRegistry internalToolRegistry,
      SkillResolver skillResolver) {
    this.toolsResolver = toolsResolver;
    this.gatewayToolHandlers = gatewayToolHandlers;
    this.agentInstanceClient = agentInstanceClient;
    this.internalToolRegistry = internalToolRegistry;
    this.skillResolver = skillResolver;
  }

  @Override
  public AgentInitializationResult initializeAgent(AgentExecutionContext executionContext) {
    AgentContext agentContext =
        Optional.ofNullable(executionContext.initialAgentContext())
            .orElseGet(() -> provisionAgentInstance(executionContext));

    List<ToolCallResult> initialToolCallResults =
        Optional.ofNullable(executionContext.initialToolCallResults()).orElseGet(List::of);

    return switch (agentContext.state()) {
      case INITIALIZING ->
          beginToolDiscovery(executionContext, agentContext, initialToolCallResults);
      case TOOL_DISCOVERY -> completeToolDiscovery(agentContext, initialToolCallResults);
      default -> resumeReadyAgent(executionContext, agentContext, initialToolCallResults);
    };
  }

  /**
   * Creates the initial agent context by first registering the agent instance on the engine. The
   * returned key is embedded in the context metadata.
   *
   * @throws io.camunda.connector.api.error.ConnectorException with code {@code
   *     AGENT_INSTANCE_CREATION_FAILED} when retries are exhausted or a non-retryable error occurs
   */
  private AgentContext provisionAgentInstance(AgentExecutionContext executionContext) {
    final var agentInstanceKey = agentInstanceClient.create(executionContext);
    return AgentContext.empty()
        .withMetadata(
            AgentMetadata.of(executionContext.jobContext())
                .withAgentInstanceKey(agentInstanceKey.value()));
  }

  private AgentInitializationResult resumeReadyAgent(
      AgentExecutionContext executionContext,
      AgentContext agentContext,
      List<ToolCallResult> initialToolCallResults) {

    final var agentMetadata = agentContext.metadata();
    final var executionMetadata = AgentMetadata.of(executionContext.jobContext());
    if (agentMetadata == null
        || !executionMetadata.processDefinitionKey().equals(agentMetadata.processDefinitionKey())) {
      agentContext =
          toolsResolver
              .updateToolDefinitions(executionContext, agentContext)
              .withMetadata(executionMetadata);
    }

    return new ReadyToConverse(agentContext, initialToolCallResults);
  }

  private AgentInitializationResult beginToolDiscovery(
      AgentExecutionContext executionContext,
      AgentContext agentContext,
      List<ToolCallResult> initialToolCallResults) {
    // add ad-hoc tool definitions to agent context
    final var adHocToolsSchema = toolsResolver.loadAdHocToolsSchema(executionContext, agentContext);

    // Append internal tool definitions (bash/fs_read/fs_write/load_skill) when a sandbox is
    // configured. Internal tools are NOT added without a sandbox — behaviour is byte-for-byte
    // unchanged. Skill names are resolved once here and frozen into load_skill's name enum; they
    // are not re-resolved per invocation.
    final var toolDefinitions =
        executionContext.configuration().sandboxConfiguration().isPresent()
            ? appendInternalTools(
                adHocToolsSchema.toolDefinitions(), resolveSkillNames(executionContext))
            : adHocToolsSchema.toolDefinitions();

    agentContext = agentContext.withToolDefinitions(toolDefinitions);

    if (CollectionUtils.isEmpty(adHocToolsSchema.gatewayToolDefinitions())) {
      return new ReadyToConverse(agentContext.withState(AgentState.READY), initialToolCallResults);
    }

    // handle gateway tool definitions (e.g. MCP)
    return dispatchGatewayToolDiscovery(
        agentContext, initialToolCallResults, adHocToolsSchema.gatewayToolDefinitions());
  }

  /** Returns a new list containing the ad-hoc tools followed by all internal tool definitions. */
  private List<ToolDefinition> appendInternalTools(
      List<ToolDefinition> adHocTools, List<String> skillNames) {
    final var combined = new ArrayList<>(adHocTools);
    combined.addAll(internalToolRegistry.toolDefinitions(skillNames));
    return List.copyOf(combined);
  }

  /**
   * Resolves the configured skill names once at initialization so {@code load_skill}'s {@code name}
   * parameter can be constrained to a frozen enum. Resolution failures are non-fatal: the generic
   * (enum-less) definition is used and the agent can still initialize.
   */
  private List<String> resolveSkillNames(AgentExecutionContext executionContext) {
    final var skillDocs = executionContext.configuration().skills();
    if (CollectionUtils.isEmpty(skillDocs)) {
      return List.of();
    }
    try {
      return skillResolver.resolveMetadata(skillDocs).stream().map(SkillMetadata::name).toList();
    } catch (Exception e) {
      LOGGER.warn("Failed to resolve skill names for the load_skill enum: {}", e.getMessage(), e);
      return List.of();
    }
  }

  private AgentInitializationResult dispatchGatewayToolDiscovery(
      AgentContext agentContext,
      List<ToolCallResult> initialToolCallResults,
      List<GatewayToolDefinition> gatewayToolDefinitions) {
    GatewayToolDiscoveryInitiationResult initiationResult =
        gatewayToolHandlers.initiateToolDiscovery(agentContext, gatewayToolDefinitions);
    agentContext = initiationResult.agentContext();

    if (!CollectionUtils.isEmpty(initiationResult.toolDiscoveryToolCalls())) {
      // execute tool discovery tool calls before agent is ready for requests
      final List<ToolCall> discoveryToolCalls = initiationResult.toolDiscoveryToolCalls();
      return new DiscoverTools(
          agentContext.withState(AgentState.TOOL_DISCOVERY), discoveryToolCalls);
    } else {
      // no tool discovery needed -> agent is ready for requests
      return new ReadyToConverse(agentContext.withState(AgentState.READY), initialToolCallResults);
    }
  }

  private AgentInitializationResult completeToolDiscovery(
      AgentContext agentContext, List<ToolCallResult> initialToolCallResults) {
    if (!gatewayToolHandlers.allToolDiscoveryResultsPresent(agentContext, initialToolCallResults)) {
      return new DeferConversation();
    }

    final var gatewayToolDiscoveryResult =
        gatewayToolHandlers.handleToolDiscoveryResults(agentContext, initialToolCallResults);

    return new ReadyToConverse(
        gatewayToolDiscoveryResult.agentContext().withState(AgentState.READY),
        gatewayToolDiscoveryResult.remainingToolCallResults());
  }
}
