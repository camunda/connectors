/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agents.aiagent.model.request;

import io.camunda.connector.agents.aiagent.model.AgentContext;
import io.camunda.connector.feel.annotation.FEEL;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

public record AgentRequest(ProviderConfiguration provider, AgentRequestData data) {
  public record AgentRequestData(
      @FEEL
          @TemplateProperty(
              label = "Agent Context",
              group = "context",
              id = "agentContext",
              description = "The agent context variable containing the conversation history",
              type = TemplateProperty.PropertyType.Text,
              feel = Property.FeelMode.required)
          @Valid
          @NotNull
          AgentContext context,
      @Valid @NotNull SystemPromptConfiguration systemPrompt,
      @Valid @NotNull UserPromptConfiguration userPrompt,
      @Valid @NotNull ToolsConfiguration tools,
      @Valid @NotNull HistoryConfiguration history,
      @Valid @NotNull GuardrailsConfiguration guardrails) {
    public record SystemPromptConfiguration(
        @FEEL
            @TemplateProperty(
                label = "System Prompt",
                group = "prompt",
                id = "systemPrompt",
                description = "Specify the system prompt",
                type = TemplateProperty.PropertyType.Text,
                feel = Property.FeelMode.optional)
            @NotBlank
            String systemPrompt) {}

    public record UserPromptConfiguration(
        @FEEL
            @TemplateProperty(
                label = "User Prompt",
                group = "prompt",
                id = "userPrompt",
                description = "Specify the user prompt",
                type = TemplateProperty.PropertyType.Text,
                feel = Property.FeelMode.optional)
            @NotBlank
            String userPrompt) {}

    public record ToolsConfiguration(
        @TemplateProperty(
                group = "tools",
                label = "Ad-hoc subprocess ID containing tools",
                description = "The ID of the subprocess containing the tools to be called",
                optional = true)
            String containerElementId,
        @FEEL
            @TemplateProperty(
                group = "tools",
                label = "Tool Call Results",
                description = "Tool call results as returned by the subprocess",
                type = TemplateProperty.PropertyType.Text,
                feel = Property.FeelMode.required,
                optional = true)
            List<Map<String, Object>> toolCallResults) {}

    public record HistoryConfiguration(
        @TemplateProperty(
                group = "history",
                label = "Maximum amount of messages to keep in history",
                defaultValue = "20")
            @NotNull
            @Min(3)
            Integer maxMessages) {}

    public record GuardrailsConfiguration(
        // TODO think of other guardrails (max tool calls, max tokens, ...)
        @TemplateProperty(
                group = "guardrails",
                label = "Maximum number of calls to the model",
                defaultValue = "10")
            @NotNull
            @Min(1)
            Integer maxModelCalls) {}
  }
}
