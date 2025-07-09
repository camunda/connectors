/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.adhoctoolsschema.resolver.AdHocToolsSchemaResolver;
import io.camunda.connector.agenticai.aiagent.agent.AgentInitializationResult.AgentContextInitializationResult;
import io.camunda.connector.agenticai.aiagent.agent.AgentInitializationResult.AgentResponseInitializationResult;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolDiscoveryInitiationResult;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.model.tool.GatewayToolDefinition;
import io.camunda.connector.agenticai.model.tool.ToolCallProcessVariable;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.springframework.util.CollectionUtils;

public class AgentInitializerImpl implements AgentInitializer {

  private final AdHocToolsSchemaResolver schemaResolver;
  private final GatewayToolHandlerRegistry gatewayToolHandlers;

  public AgentInitializerImpl(
      AdHocToolsSchemaResolver schemaResolver, GatewayToolHandlerRegistry gatewayToolHandlers) {
    this.schemaResolver = schemaResolver;
    this.gatewayToolHandlers = gatewayToolHandlers;
  }

  @Override
  public AgentInitializationResult initializeAgent(AgentExecutionContext executionContext) {
    AgentContext agentContext =
        Optional.ofNullable(executionContext.request().data().context())
            .orElseGet(AgentContext::empty);

    List<ToolCallResult> toolCallResults =
        Optional.ofNullable(executionContext.request().data().tools())
            .map(AgentRequest.AgentRequestData.ToolsConfiguration::toolCallResults)
            .orElseGet(Collections::emptyList);

    return switch (agentContext.state()) {
      case INITIALIZING -> initiateToolDiscovery(executionContext, agentContext, toolCallResults);
      case TOOL_DISCOVERY -> handleToolDiscoveryResults(agentContext, toolCallResults);
      default -> new AgentContextInitializationResult(agentContext, toolCallResults);
    };
  }

  private AgentInitializationResult initiateToolDiscovery(
      AgentExecutionContext executionContext,
      AgentContext agentContext,
      List<ToolCallResult> toolCallResults) {
    final var toolsContainerElementId =
        Optional.ofNullable(executionContext.request().data().tools())
            .map(AgentRequest.AgentRequestData.ToolsConfiguration::containerElementId)
            .filter(id -> !id.isBlank())
            .orElse(null);

    // no ad-hoc sub-process element ID provided, skip tool discovery
    if (toolsContainerElementId == null) {
      return new AgentContextInitializationResult(
          agentContext.withState(AgentState.READY), toolCallResults);
    }

    final var adHocToolsSchema =
        schemaResolver.resolveSchema(
            executionContext.jobContext().processDefinitionKey(), toolsContainerElementId);

    // add ad-hoc tool definitions to agent context
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
    final var gatewayToolDiscoveryResult =
        gatewayToolHandlers.handleToolDiscoveryResults(agentContext, toolCallResults);

    return new AgentContextInitializationResult(
        gatewayToolDiscoveryResult.agentContext().withState(AgentState.READY),
        gatewayToolDiscoveryResult.remainingToolCallResults());
  }
}
