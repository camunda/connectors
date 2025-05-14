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
    defaultResultVariable = "agent",
    propertyGroups = {
      @PropertyGroup(id = "provider", label = "Model Provider", openByDefault = false),
      @PropertyGroup(id = "model", label = "Model", openByDefault = false),
      @PropertyGroup(
          id = "systemPrompt",
          label = "System Prompt",
          tooltip =
              "A system prompt is a set of foundational instructions given to an AI agent before any user interaction begins."
                  + "It defines the AI’s role, behavior, tone, and communication style, ensuring that responses remain consistent "
                  + "and aligned with its intended purpose. These instructions help shape how the AI interprets and responds "
                  + "to user input throughout the conversation.",
          openByDefault = false),
      @PropertyGroup(
          id = "userPrompt",
          label = "User Prompt",
          tooltip =
              "A user prompt is the message or question you give to the AI to start or continue a conversation. It tells "
                  + "the AI what you need, whether it's information, help with a task, or just a chat. The AI uses your prompt "
                  + "to understand how to respond.",
          openByDefault = false),
      @PropertyGroup(
          id = "tools",
          label = "Tools",
          tooltip =
              "Optional tools which should be made available to the agent. Configure this group if you AI Agent should be part of a tools feedback loop.",
          openByDefault = false),
      @PropertyGroup(
          id = "memory",
          label = "Memory",
          tooltip = "Configuration of the Agent's short-term/conversational memory.",
          openByDefault = false),
      @PropertyGroup(id = "limits", label = "Limits", openByDefault = false)
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
