/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent;

import io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.ProcessDefinitionAdHocToolElementsResolver;
import io.camunda.connector.agenticai.aiagent.agent.OutboundConnectorAgentRequestHandler;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.agenticai.aiagent.model.OutboundConnectorAgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.OutboundConnectorAgentJobContext;
import io.camunda.connector.agenticai.aiagent.model.request.OutboundConnectorAgentRequest;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate.PropertyGroup;

/**
 * AI Agent outbound connector implementation (acting on a service task).
 *
 * <p>Type and timeout can be overriden by setting the following environment variables:
 *
 * <ul>
 *   <li>CONNECTOR_AI_AGENT_TYPE
 *   <li>CONNECTOR_AI_AGENT_TIMEOUT
 * </ul>
 */
@OutboundConnector(
    name = "AI Agent",
    inputVariables = {"provider", "data"},
    type = "io.camunda.agenticai:aiagent:1")
@ElementTemplate(
    id = "io.camunda.connectors.agenticai.aiagent.v1",
    name = "AI Agent Task",
    description =
        "Handles individual tasks by aligning user requests with AI responses, optimized for focused, single-goal queries.",
    metadata = @ElementTemplate.Metadata(keywords = {"AI", "AI Agent", "agentic orchestration"}),
    documentationRef =
        "https://docs.camunda.io/docs/8.8/components/connectors/out-of-the-box-connectors/agentic-ai-aiagent/",
    engineVersion = "^8.8",
    version = 3,
    inputDataClass = OutboundConnectorAgentRequest.class,
    outputDataClass = AgentResponse.class,
    defaultResultVariable = "agent",
    propertyGroups = {
      @PropertyGroup(id = "provider", label = "Model provider", openByDefault = false),
      @PropertyGroup(id = "model", label = "Model", openByDefault = false),
      @PropertyGroup(
          id = "systemPrompt",
          label = "System prompt",
          tooltip =
              "A system prompt is a set of foundational instructions given to a model before any user interaction begins. "
                  + "It defines the AI agent’s role, behavior, tone, and communication style, ensuring that responses remain consistent "
                  + "and aligned with the AI agent’s intended purpose. These instructions help shape how the model interprets and responds "
                  + "to user input throughout the conversation.",
          openByDefault = false),
      @PropertyGroup(
          id = "userPrompt",
          label = "User prompt",
          tooltip =
              "A user prompt is the message or question you give to the AI to start or continue a conversation. It tells "
                  + "the AI what you need, whether it's information, help with a task, or just a chat. The AI uses your prompt "
                  + "to understand how to respond.",
          openByDefault = false),
      @PropertyGroup(
          id = "tools",
          label = "Tools",
          tooltip =
              "Tools are optional features the AI Agent can use to perform specific tasks. Configure this if the agent should participate in a tools feedback loop.",
          openByDefault = false),
      @PropertyGroup(
          id = "memory",
          label = "Memory",
          tooltip = "Configuration of the Agent's short-term/conversational memory.",
          openByDefault = false),
      @PropertyGroup(id = "limits", label = "Limits", openByDefault = false),
      @PropertyGroup(
          id = "response",
          label = "Response",
          tooltip =
              "Configuration of the model response format and how to map the model response to the connector result.<br><br>Depending on the selection, the model response will be available as <code>response.responseText</code> or <code>response.responseJson</code>.<br><br>See <a href=\"https://docs.camunda.io/docs/8.8/components/connectors/out-of-the-box-connectors/agentic-ai-aiagent-task/#response\">documentation</a> for details.",
          openByDefault = false)
    },
    icon = "aiagent.svg")
public class AiAgentFunction implements OutboundConnectorFunction {
  private final ProcessDefinitionAdHocToolElementsResolver toolElementsResolver;
  private final OutboundConnectorAgentRequestHandler agentRequestHandler;

  public AiAgentFunction(
      ProcessDefinitionAdHocToolElementsResolver toolElementsResolver,
      OutboundConnectorAgentRequestHandler agentRequestHandler) {
    this.toolElementsResolver = toolElementsResolver;
    this.agentRequestHandler = agentRequestHandler;
  }

  @Override
  public AgentResponse execute(OutboundConnectorContext context) {
    final OutboundConnectorAgentRequest request =
        context.bindVariables(OutboundConnectorAgentRequest.class);
    final OutboundConnectorAgentExecutionContext executionContext =
        new OutboundConnectorAgentExecutionContext(
            new OutboundConnectorAgentJobContext(context), request, toolElementsResolver);

    return agentRequestHandler.handleRequest(executionContext);
  }
}
