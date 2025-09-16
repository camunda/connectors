/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.SystemPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.ProviderConfiguration;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyConstraints;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record OutboundConnectorAgentRequest(
    @Valid @NotNull ProviderConfiguration provider,
    @Valid @NotNull OutboundConnectorAgentRequestData data) {

  public record OutboundConnectorAgentRequestData(
      @FEEL
          @TemplateProperty(
              label = "Agent context",
              group = "memory",
              id = "agentContext",
              description =
                  "Avoid reusing context variables across agents to prevent issues with stale data or tool access.",
              tooltip =
                  "The agent context variable containing all relevant data for the agent to support the feedback loop between "
                      + "user requests, tool calls and LLM responses. Make sure this variable points to the <code>context</code> "
                      + "variable which is returned from the agent response. "
                      + "<a href=\"https://docs.camunda.io/docs/8.8/components/connectors/out-of-the-box-connectors/agentic-ai-aiagent/\" target=\"_blank\">See documentation</a> "
                      + "for details.",
              constraints = @PropertyConstraints(notEmpty = true),
              type = TemplateProperty.PropertyType.Text,
              feel = Property.FeelMode.required,
              defaultValue = "=agent.context")
          @Valid
          AgentContext context,
      @Valid @NotNull SystemPromptConfiguration systemPrompt,
      @Valid @NotNull UserPromptConfiguration userPrompt,
      @Valid ToolsConfiguration tools,
      @Valid MemoryConfiguration memory,
      @Valid LimitsConfiguration limits,
      @Valid OutboundConnectorResponseConfiguration response) {}
}
