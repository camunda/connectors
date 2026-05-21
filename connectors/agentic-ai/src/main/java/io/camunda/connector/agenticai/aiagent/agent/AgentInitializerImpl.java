/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.agent.AgentInitializationResult.AgentContextInitializationResult;
import io.camunda.connector.agenticai.aiagent.agent.AgentInitializationResult.AgentDiscoveryInProgressInitializationResult;
import io.camunda.connector.agenticai.aiagent.agent.AgentInitializationResult.AgentResponseInitializationResult;
import io.camunda.connector.agenticai.aiagent.agentinstance.AgentInstanceClient;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentMetadata;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolDiscoveryInitiationResult;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.model.tool.GatewayToolDefinition;
import io.camunda.connector.agenticai.model.tool.ToolCallProcessVariable;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import java.util.List;
import java.util.Optional;
import org.springframework.util.CollectionUtils;

public class AgentInitializerImpl implements AgentInitializer {

  private final AgentToolsResolver toolsResolver;
  private final GatewayToolHandlerRegistry gatewayToolHandlers;
  private final AgentInstanceClient agentInstanceClient;

  public AgentInitializerImpl(
      AgentToolsResolver toolsResolver,
      GatewayToolHandlerRegistry gatewayToolHandlers,
      AgentInstanceClient agentInstanceClient) {
    this.toolsResolver = toolsResolver;
    this.gatewayToolHandlers = gatewayToolHandlers;
    this.agentInstanceClient = agentInstanceClient;
  }

  @Override
  public AgentInitializationResult initializeAgent(AgentExecutionContext executionContext) {
    AgentContext agentContext =
        Optional.ofNullable(executionContext.initialAgentContext())
            .orElseGet(() -> createAgentContext(executionContext));

    List<ToolCallResult> toolCallResults =
        Optional.ofNullable(executionContext.initialToolCallResults()).orElseGet(List::of);

    return switch (agentContext.state()) {
      case INITIALIZING -> initiateToolDiscovery(executionContext, agentContext, toolCallResults);
      case TOOL_DISCOVERY -> handleToolDiscoveryResults(agentContext, toolCallResults);
      default -> handleReadyState(executionContext, agentContext, toolCallResults);
    };
  }

  /**
   * Creates the initial agent context by first registering the agent instance on the engine. The
   * returned key is embedded in the context metadata.
   *
   * @throws io.camunda.connector.api.error.ConnectorException with code {@code
   *     AGENT_INSTANCE_CREATION_FAILED} when retries are exhausted or a non-retryable error occurs
   */
  private AgentContext createAgentContext(AgentExecutionContext executionContext) {
    final var agentInstanceKey = agentInstanceClient.create(executionContext);
    return AgentContext.empty()
        .withMetadata(
            AgentMetadata.of(executionContext.jobContext())
                .withAgentInstanceKey(agentInstanceKey.value()));
  }

  private AgentInitializationResult handleReadyState(
      AgentExecutionContext executionContext,
      AgentContext agentContext,
      List<ToolCallResult> toolCallResults) {

    final var agentMetadata = agentContext.metadata();
    final var executionMetadata = AgentMetadata.of(executionContext.jobContext());
    if (agentMetadata == null
        || !executionMetadata.processDefinitionKey().equals(agentMetadata.processDefinitionKey())) {
      agentContext =
          toolsResolver
              .updateToolDefinitions(executionContext, agentContext)
              .withMetadata(executionMetadata);
    }

    return new AgentContextInitializationResult(agentContext, toolCallResults);
  }

  private AgentInitializationResult initiateToolDiscovery(
      AgentExecutionContext executionContext,
      AgentContext agentContext,
      List<ToolCallResult> toolCallResults) {
    // add ad-hoc tool definitions to agent context
    final var adHocToolsSchema = toolsResolver.loadAdHocToolsSchema(executionContext, agentContext);
    agentContext = agentContext.withToolDefinitions(adHocToolsSchema.toolDefinitions());

    if (CollectionUtils.isEmpty(adHocToolsSchema.gatewayToolDefinitions())) {
      return new AgentContextInitializationResult(
          agentContext.withState(AgentState.READY), toolCallResults);
    }

    // handle gateway tool definitions (e.g. MCP)
    return initiateGatewayToolDiscovery(
        agentContext, toolCallResults, adHocToolsSchema.gatewayToolDefinitions());
  }

  private AgentInitializationResult initiateGatewayToolDiscovery(
      AgentContext agentContext,
      List<ToolCallResult> toolCallResults,
      List<GatewayToolDefinition> gatewayToolDefinitions) {
    GatewayToolDiscoveryInitiationResult initiationResult =
        gatewayToolHandlers.initiateToolDiscovery(agentContext, gatewayToolDefinitions);
    agentContext = initiationResult.agentContext();

    if (!CollectionUtils.isEmpty(initiationResult.toolDiscoveryToolCalls())) {
      // execute tool discovery tool calls before agent is ready for requests
      return new AgentResponseInitializationResult(
          AgentResponse.builder()
              .context(agentContext.withState(AgentState.TOOL_DISCOVERY))
              .toolCalls(
                  initiationResult.toolDiscoveryToolCalls().stream()
                      .map(ToolCallProcessVariable::from)
                      .toList())
              .build());
    } else {
      // no tool discovery needed -> agent is ready for requests
      return new AgentContextInitializationResult(
          agentContext.withState(AgentState.READY), toolCallResults);
    }
  }

  private AgentInitializationResult handleToolDiscoveryResults(
      AgentContext agentContext, List<ToolCallResult> toolCallResults) {
    if (!gatewayToolHandlers.allToolDiscoveryResultsPresent(agentContext, toolCallResults)) {
      return new AgentDiscoveryInProgressInitializationResult();
    }

    final var gatewayToolDiscoveryResult =
        gatewayToolHandlers.handleToolDiscoveryResults(agentContext, toolCallResults);

    return new AgentContextInitializationResult(
        gatewayToolDiscoveryResult.agentContext().withState(AgentState.READY),
        gatewayToolDiscoveryResult.remainingToolCallResults());
  }
}
