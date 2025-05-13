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
    name = "AI Agent (alpha)",
    inputVariables = {"provider", "data"},
    type = "io.camunda.agenticai:aiagent:0")
@ElementTemplate(
    id = "io.camunda.connectors.agenticai.aiagent.v0",
    name = "AI Agent (alpha)",
    description =
        "Provides a default AI Agent implementation handling the feedback loop between user requests, tool calls and LLM responses.",
    engineVersion = "^8.8",
    version = 0,
    inputDataClass = AgentRequest.class,
    propertyGroups = {
      @PropertyGroup(id = "model", label = "Model"),
      @PropertyGroup(id = "authentication", label = "Authentication"),
      @PropertyGroup(id = "systemPrompt", label = "System Prompt"),
      @PropertyGroup(id = "userPrompt", label = "User Prompt"),
      @PropertyGroup(
          id = "tools",
          label = "Tools",
          tooltip = "Configuration of tools which should be made available to the agent."),
      @PropertyGroup(
          id = "memory",
          label = "Memory",
          tooltip = "Configuration of the Agent's short-term memory."),
      @PropertyGroup(id = "limits", label = "Limits"),
      @PropertyGroup(
          id = "parameters",
          label = "Model Parameters",
          tooltip =
              "Configuration of common model parameters to optimize and fine-tune LLM responses. Limits such as maximum output tokens are <strong>per LLM request</strong>.",
          openByDefault = false)
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
