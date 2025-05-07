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
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyConstraints;
import io.camunda.document.Document;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

public record AgentRequest(
    @Valid @NotNull ProviderConfiguration provider, @Valid @NotNull AgentRequestData data) {
  public record AgentRequestData(
      @FEEL
          @TemplateProperty(
              label = "Agent Context",
              group = "memory",
              id = "agentContext",
              description =
                  "The agent context variable containing all relevant data for the agent to support the feedback loop between user requests, tool calls and LLM responses. Make sure this variable points to the <code>context</code> variable which is returned from the agent response.",
              constraints = @PropertyConstraints(notEmpty = true),
              type = TemplateProperty.PropertyType.Text,
              feel = Property.FeelMode.required)
          @Valid
          AgentContext context,
      @Valid @NotNull SystemPromptConfiguration systemPrompt,
      @Valid @NotNull UserPromptConfiguration userPrompt,
      @Valid @NotNull ToolsConfiguration tools,
      @Valid @NotNull MemoryConfiguration memory,
      @Valid @NotNull LimitsConfiguration limits) {

    public interface PromptConfiguration {
      String PROMPT_PARAMETERS_DESCRIPTION =
          "Map of parameters which can be used in <code>{{parameter}}</code> format in the prompt text.";
      String PROMPT_PARAMETERS_TOOLTIP =
          "Default parameters provided by the integration: <code>current_date</code>, <code>current_time</code>, <code>current_date_time</code>";

      String prompt();

      Map<String, Object> parameters();
    }

    public record SystemPromptConfiguration(
        @FEEL
            @TemplateProperty(
                group = "systemPrompt",
                label = "System Prompt",
                type = TemplateProperty.PropertyType.Text,
                feel = Property.FeelMode.optional,
                constraints = @PropertyConstraints(notEmpty = true),
                defaultValue = DEFAULT_SYSTEM_PROMPT)
            @NotBlank
            String prompt,
        @FEEL
            @TemplateProperty(
                group = "systemPrompt",
                label = "System Prompt Parameters",
                description = PROMPT_PARAMETERS_DESCRIPTION,
                tooltip = PROMPT_PARAMETERS_TOOLTIP,
                feel = Property.FeelMode.required,
                optional = true)
            Map<String, Object> parameters)
        implements PromptConfiguration {

      @TemplateProperty(ignore = true)
      public static final String DEFAULT_SYSTEM_PROMPT =
          """
You are **TaskAgent**, a helpful, generic chat agent that can handle a wide variety of customer requests using your own domain knowledge **and** any tools explicitly provided to you at runtime.

If tools are provided, you should prefer them instead of guessing an answer. You can call the same tool multiple times by providing different input values. Don't guess any tools which were not explicitely configured. If no tool matches the request, try to generate an answer. If you're not able to find a good answer, return with a message stating why you're not able to.

Wrap minimal, inspectable reasoning in *exactly* this XML template:

<thinking>
  <context>…briefly state the customer’s need and current state…</context>
  <reflection>…list candidate tools, justify which you will call next and why…</reflection>
</thinking>

Reveal **no** additional private reasoning outside these tags.
""";
    }

    public record UserPromptConfiguration(
        @FEEL
            @TemplateProperty(
                group = "userPrompt",
                label = "User Prompt",
                type = TemplateProperty.PropertyType.Text,
                feel = Property.FeelMode.optional,
                constraints = @PropertyConstraints(notEmpty = true))
            @NotBlank
            String prompt,
        @FEEL
            @TemplateProperty(
                group = "userPrompt",
                label = "User Prompt Parameters",
                description = PROMPT_PARAMETERS_DESCRIPTION,
                tooltip = PROMPT_PARAMETERS_TOOLTIP,
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
                label = "Ad-hoc subprocess ID",
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
        // TODO support more advanced eviction policies (token window)
        @TemplateProperty(
                group = "memory",
                label = "Maximum messages",
                description =
                    "Maximum amount of messages to keep in short-term/conversation memory.",
                type = TemplateProperty.PropertyType.Number,
                defaultValue = "20",
                defaultValueType = TemplateProperty.DefaultValueType.Number,
                constraints = @PropertyConstraints(notEmpty = true))
            @NotNull
            @Min(3)
            Integer maxMessages) {}

    public record LimitsConfiguration(
        // TODO think of other limits (max tool calls, max tokens, ...)
        @TemplateProperty(
                group = "limits",
                label = "Maximum model calls",
                description =
                    "Maximum number of calls to the model as a safety limit to prevent infinite loops.",
                type = TemplateProperty.PropertyType.Number,
                defaultValue = "10",
                defaultValueType = TemplateProperty.DefaultValueType.Number)
            @NotNull
            @Min(1)
            Integer maxModelCalls) {}
  }
}
