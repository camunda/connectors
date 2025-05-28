/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.tool;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.model.tool.GatewayToolDefinition;
import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

public class GatewayToolHandlerRegistryImpl implements GatewayToolHandlerRegistry {
  private static final String DEFAULT_TYPE = "_default";

  private final Map<String, GatewayToolHandler> handlers = new LinkedHashMap<>();

  public GatewayToolHandlerRegistryImpl(List<GatewayToolHandler> handlers) {
    handlers.forEach(this::register);
  }

  public void register(GatewayToolHandler handler) {
    final var type = Optional.ofNullable(handler).map(GatewayToolHandler::type).orElse(null);
    if (StringUtils.isBlank(type) || type.equals(DEFAULT_TYPE)) {
      throw new IllegalArgumentException("Invalid gateway tool handler type: %s".formatted(type));
    }

    if (handlers.containsKey(type)) {
      throw new IllegalArgumentException("Duplicate gateway tool handler type: %s".formatted(type));
    }

    handlers.put(type, handler);
  }

  @Override
  public GatewayToolDiscoveryInitiationResult initiateToolDiscovery(
      final AgentContext agentContext, final List<GatewayToolDefinition> gatewayToolDefinitions) {
    var updatedAgentContext = agentContext;

    // initiate tool discovery
    List<ToolCall> toolDiscoveryToolCalls = new ArrayList<>();
    for (GatewayToolHandler gatewayToolHandler : handlers.values()) {
      GatewayToolDiscoveryInitiationResult handlerResult =
          gatewayToolHandler.initiateToolDiscovery(agentContext, gatewayToolDefinitions);

      // update agent context with updated context from discovery (e.g. added properties)
      updatedAgentContext = handlerResult.agentContext();
      toolDiscoveryToolCalls.addAll(handlerResult.toolDiscoveryToolCalls());
    }

    return new GatewayToolDiscoveryInitiationResult(
        updatedAgentContext, List.copyOf(toolDiscoveryToolCalls));
  }

  @Override
  public GatewayToolDiscoveryResult handleToolDiscoveryResults(
      final AgentContext agentContext, final List<ToolCallResult> toolCallResults) {
    // group tool call results by gateway type - keep non-gateway tool call results in DEFAULT_TYPE
    Map<String, List<ToolCallResult>> groupedByGateway =
        groupToolCallResultsByGateway(toolCallResults);

    // merge tool definitions from all gateways to existing tool definitions
    List<ToolDefinition> mergedToolDefinitions = new ArrayList<>(agentContext.toolDefinitions());
    groupedByGateway.entrySet().stream()
        .filter(entry -> !entry.getKey().equals(DEFAULT_TYPE))
        .forEach(
            entry -> {
              final var handler = handlers.get(entry.getKey());
              final var gatewayToolDefinitions =
                  handler.handleToolDiscoveryResults(agentContext, entry.getValue());
              mergedToolDefinitions.addAll(gatewayToolDefinitions);
            });

    // remaining tool call results not being part of tool discovery
    final var nonGatewayToolCallResults =
        groupedByGateway.getOrDefault(DEFAULT_TYPE, Collections.emptyList());

    return new GatewayToolDiscoveryResult(
        agentContext, List.copyOf(mergedToolDefinitions), nonGatewayToolCallResults);
  }

  private Map<String, List<ToolCallResult>> groupToolCallResultsByGateway(
      final List<ToolCallResult> toolCallResults) {
    return toolCallResults.stream()
        .collect(
            Collectors.groupingBy(
                toolCallResult -> {
                  for (GatewayToolHandler gatewayToolHandler : handlers.values()) {
                    if (gatewayToolHandler.handlesToolDiscoveryResult(toolCallResult)) {
                      return gatewayToolHandler.type();
                    }
                  }

                  return DEFAULT_TYPE;
                }));
  }

  @Override
  public List<ToolCall> transformToolCalls(
      final AgentContext agentContext, final List<ToolCall> toolCalls) {
    List<ToolCall> transformedToolCalls = toolCalls;
    for (GatewayToolHandler gatewayToolHandler : handlers.values()) {
      transformedToolCalls =
          gatewayToolHandler.transformToolCalls(agentContext, transformedToolCalls);
    }

    return transformedToolCalls;
  }

  @Override
  public List<ToolCallResult> transformToolCallResults(
      final AgentContext agentContext, final List<ToolCallResult> toolCallResults) {
    List<ToolCallResult> transformedToolCallResults = toolCallResults;
    for (GatewayToolHandler gatewayToolHandler : handlers.values()) {
      transformedToolCallResults =
          gatewayToolHandler.transformToolCallResults(agentContext, transformedToolCallResults);
    }

    return transformedToolCallResults;
  }
}
