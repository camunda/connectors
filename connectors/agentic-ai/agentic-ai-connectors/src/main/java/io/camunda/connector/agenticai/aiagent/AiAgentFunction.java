/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent;

import io.camunda.connector.agenticai.aiagent.agent.AiAgentRequestHandler;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate.PropertyGroup;

@OutboundConnector(
    name = "AI Agent",
    inputVariables = {"provider", "data"},
    type = "io.camunda.agenticai:aiagent:1")
@ElementTemplate(
    id = "io.camunda.connectors.agenticai.aiagent.v1",
    name = "AI Agent",
    description = "AI Agent connector",
    inputDataClass = AgentRequest.class,
    version = 1,
    propertyGroups = {
      @PropertyGroup(id = "model", label = "Model"),
      @PropertyGroup(id = "authentication", label = "Authentication"),
      @PropertyGroup(id = "parameters", label = "Parameters"),
      @PropertyGroup(id = "context", label = "Context"),
      @PropertyGroup(id = "prompt", label = "Prompt"),
      @PropertyGroup(id = "tools", label = "Tools"),
      @PropertyGroup(id = "memory", label = "Memory"),
      @PropertyGroup(id = "guardrails", label = "Guardrails"),
    },
    icon = "aiagent.svg")
public class AiAgentFunction implements OutboundConnectorFunction {
  private final AiAgentRequestHandler aiAgentRequestHandler;

  public AiAgentFunction(AiAgentRequestHandler aiAgentRequestHandler) {
    this.aiAgentRequestHandler = aiAgentRequestHandler;
  }

  @Override
  public Object execute(OutboundConnectorContext context) {
    final AgentRequest request = context.bindVariables(AgentRequest.class);
    return aiAgentRequestHandler.handleRequest(context, request);
  }
}
