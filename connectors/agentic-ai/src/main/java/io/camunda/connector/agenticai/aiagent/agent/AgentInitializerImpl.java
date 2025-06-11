/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.adhoctoolsschema.resolver.AdHocToolsSchemaResolver;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolDiscoveryInitiationResult;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.model.tool.GatewayToolDefinition;
import io.camunda.connector.agenticai.model.tool.ToolCallProcessVariable;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
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
  public AgentInitializationResult initializeAgent(
      OutboundConnectorContext context, AgentRequest request) {

    AgentContext agentContext =
        Optional.ofNullable(request.data().context()).orElseGet(AgentContext::empty);

    List<ToolCallResult> toolCallResults =
        Optional.ofNullable(request.data().tools())
            .map(AgentRequest.AgentRequestData.ToolsConfiguration::toolCallResults)
            .orElseGet(Collections::emptyList);

    return switch (agentContext.state()) {
      case INITIALIZING -> initiateToolDiscovery(context, request, agentContext, toolCallResults);
      case TOOL_DISCOVERY -> handleToolDiscoveryResults(agentContext, toolCallResults);
      default ->
          AgentInitializationResult.builder()
              .agentContext(agentContext)
              .toolCallResults(toolCallResults)
              .build();
    };
  }

  private AgentInitializationResult initiateToolDiscovery(
      OutboundConnectorContext context,
      AgentRequest request,
      AgentContext agentContext,
      List<ToolCallResult> toolCallResults) {
    final var toolsContainerElementId =
        Optional.ofNullable(request.data().tools())
            .map(AgentRequest.AgentRequestData.ToolsConfiguration::containerElementId)
            .filter(id -> !id.isBlank())
            .orElse(null);

    // no ad-hoc sub-process element ID provided, skip tool discovery
    if (toolsContainerElementId == null) {
      return AgentInitializationResult.builder()
          .agentContext(agentContext.withState(AgentState.READY))
          .toolCallResults(toolCallResults)
          .build();
    }

    final var adHocToolsSchema =
        schemaResolver.resolveSchema(
            context.getJobContext().getProcessDefinitionKey(), toolsContainerElementId);

    // add ad-hoc tool definitions to agent context
    agentContext = agentContext.withToolDefinitions(adHocToolsSchema.toolDefinitions());

    if (CollectionUtils.isEmpty(adHocToolsSchema.gatewayToolDefinitions())) {
      return AgentInitializationResult.builder()
          .agentContext(agentContext.withState(AgentState.READY))
          .toolCallResults(toolCallResults)
          .build();
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
      return AgentInitializationResult.builder()
          .agentResponse(
              AgentResponse.builder()
                  .context(agentContext.withState(AgentState.TOOL_DISCOVERY))
                  .toolCalls(
                      initiationResult.toolDiscoveryToolCalls().stream()
                          .map(ToolCallProcessVariable::from)
                          .toList())
                  .build())
          .build();
    } else {
      // no tool discovery needed -> agent is ready for requests
      return AgentInitializationResult.builder()
          .agentContext(agentContext.withState(AgentState.READY))
          .toolCallResults(toolCallResults)
          .build();
    }
  }

  private AgentInitializationResult handleToolDiscoveryResults(
      AgentContext agentContext, List<ToolCallResult> toolCallResults) {
    final var gatewayToolDiscoveryResult =
        gatewayToolHandlers.handleToolDiscoveryResults(agentContext, toolCallResults);

    return AgentInitializationResult.builder()
        .agentContext(gatewayToolDiscoveryResult.agentContext().withState(AgentState.READY))
        .toolCallResults(gatewayToolDiscoveryResult.remainingToolCallResults())
        .build();
  }
}
