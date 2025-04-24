/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.feel.annotation.FEEL;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.document.Document;
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
              description = "The agent context variable containing the conversation memory",
              type = TemplateProperty.PropertyType.Text,
              feel = Property.FeelMode.required)
          @Valid
          @NotNull
          AgentContext context,
      @Valid @NotNull SystemPromptConfiguration systemPrompt,
      @Valid @NotNull UserPromptConfiguration userPrompt,
      @Valid @NotNull ToolsConfiguration tools,
      @Valid @NotNull MemoryConfiguration memory,
      @Valid @NotNull GuardrailsConfiguration guardrails) {

    public interface PromptConfiguration {
      String prompt();

      Map<String, Object> parameters();
    }

    public record SystemPromptConfiguration(
        @FEEL
            @TemplateProperty(
                group = "systemPrompt",
                label = "System Prompt",
                type = TemplateProperty.PropertyType.Text,
                feel = Property.FeelMode.optional)
            @NotBlank
            String prompt,
        @FEEL
            @TemplateProperty(
                group = "systemPrompt",
                label = "System Prompt Parameters",
                feel = Property.FeelMode.required,
                optional = true)
            Map<String, Object> parameters)
        implements PromptConfiguration {}

    public record UserPromptConfiguration(
        @FEEL
            @TemplateProperty(
                group = "userPrompt",
                label = "User Prompt",
                type = TemplateProperty.PropertyType.Text,
                feel = Property.FeelMode.optional)
            @NotBlank
            String prompt,
        @FEEL
            @TemplateProperty(
                group = "userPrompt",
                label = "User Prompt Parameters",
                feel = Property.FeelMode.required,
                optional = true)
            Map<String, Object> parameters,
        @FEEL
            @TemplateProperty(
                group = "userPrompt",
                label = "Documents",
                description = "Documents to be included in the user prompt",
                tooltip = "Referenced documents will be transparently added to the user prompt.",
                feel = Property.FeelMode.required,
                optional = true)
            List<Document> documents)
        implements PromptConfiguration {}

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

    public record MemoryConfiguration(
        @TemplateProperty(
                group = "memory",
                label = "Maximum amount of messages to keep in memory",
                type = TemplateProperty.PropertyType.Number,
                defaultValue = "20",
                defaultValueType = TemplateProperty.DefaultValueType.Number)
            @NotNull
            @Min(3)
            Integer maxMessages) {}

    public record GuardrailsConfiguration(
        // TODO think of other guardrails (max tool calls, max tokens, ...)
        @TemplateProperty(
                group = "guardrails",
                label = "Maximum number of calls to the model",
                type = TemplateProperty.PropertyType.Number,
                defaultValue = "10",
                defaultValueType = TemplateProperty.DefaultValueType.Number)
            @NotNull
            @Min(1)
            Integer maxModelCalls) {}
  }
}
