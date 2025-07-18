/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.feel.annotation.FEEL;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.NestedProperties;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyConstraints;
import io.camunda.document.Document;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Map;

public record AgentRequest(
    @Valid @NotNull ProviderConfiguration provider, @Valid @NotNull AgentRequestData data) {
  public record AgentRequestData(
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
      @Valid ResponseConfiguration response) {

    public interface PromptConfiguration {
      String PROMPT_PARAMETERS_DESCRIPTION =
          "Use <code>{{parameter}}</code> format in the prompt to insert values defined in this map.";

      String prompt();

      Map<String, Object> parameters();
    }

    public record SystemPromptConfiguration(
        @FEEL
            @TemplateProperty(
                group = "systemPrompt",
                label = "System prompt",
                type = TemplateProperty.PropertyType.Text,
                feel = Property.FeelMode.optional,
                constraints = @PropertyConstraints(notEmpty = true),
                defaultValue = DEFAULT_SYSTEM_PROMPT)
            String prompt,
        @FEEL
            @TemplateProperty(
                group = "systemPrompt",
                label = "System prompt parameters",
                description = PROMPT_PARAMETERS_DESCRIPTION,
                feel = Property.FeelMode.required,
                optional = true)
            Map<
                    @NotBlank(message = "System prompt parameter key must not be blank")
                    @Pattern(
                        regexp = "^[a-zA-Z0-9_]+$",
                        message =
                            "System prompt parameter key can only contain letters, digits, or underscores")
                    String,
                    Object>
                parameters)
        implements PromptConfiguration {

      @TemplateProperty(ignore = true)
      public static final String DEFAULT_SYSTEM_PROMPT =
          """
You are **TaskAgent**, a helpful, generic chat agent that can handle a wide variety of customer requests using your own domain knowledge **and** any tools explicitly provided to you at runtime.

If tools are provided, you should prefer them instead of guessing an answer. You can call the same tool multiple times by providing different input values. Don't guess any tools which were not explicitly configured. If no tool matches the request, try to generate an answer. If you're not able to find a good answer, return with a message stating why you're not able to.

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
                label = "User prompt",
                type = TemplateProperty.PropertyType.Text,
                feel = Property.FeelMode.optional,
                constraints = @PropertyConstraints(notEmpty = true))
            String prompt,
        @FEEL
            @TemplateProperty(
                group = "userPrompt",
                label = "User prompt parameters",
                description = PROMPT_PARAMETERS_DESCRIPTION,
                feel = Property.FeelMode.required,
                optional = true)
            Map<
                    @NotBlank(message = "User prompt parameter key must not be blank")
                    @Pattern(
                        regexp = "^[a-zA-Z0-9_]+$",
                        message =
                            "User prompt parameter key can only contain letters, digits, or underscores")
                    String,
                    Object>
                parameters,
        @FEEL
            @TemplateProperty(
                group = "userPrompt",
                label = "Documents",
                description = "Documents to be included in the user prompt.",
                tooltip =
                    "Referenced documents will be automatically added to the user prompt. "
                        + "<a href=\"https://docs.camunda.io/docs/8.8/components/connectors/out-of-the-box-connectors/agentic-ai-aiagent/\" target=\"_blank\">See documentation</a> "
                        + "for details and supported file types.",
                feel = Property.FeelMode.required,
                optional = true)
            List<Document> documents)
        implements PromptConfiguration {}

    public record ToolsConfiguration(
        @TemplateProperty(
                group = "tools",
                label = "Ad-hoc sub-process ID",
                description = "ID of the sub-process that contains the tools the AI agent can use.",
                tooltip =
                    "Add an ad-hoc sub-process ID to attach the AI agent to the tools. Ensure your process includes a tools "
                        + "feedback loop routing into the ad-hoc sub-process and back to the AI agent connector. "
                        + "<a href=\"https://docs.camunda.io/docs/8.8/components/connectors/out-of-the-box-connectors/agentic-ai-aiagent/\" target=\"_blank\">See documentation</a> "
                        + "for details.",
                optional = true)
            String containerElementId,
        @FEEL
            @TemplateProperty(
                group = "tools",
                label = "Tool call results",
                description = "Tool call results as returned by the sub-process.",
                tooltip =
                    "This defines where to handle tool call results returned by the ad-hoc sub-process. Model this "
                        + "as part of your process and route it into the tools feedback loop. "
                        + "<a href=\"https://docs.camunda.io/docs/8.8/components/connectors/out-of-the-box-connectors/agentic-ai-aiagent/\" target=\"_blank\">See documentation</a> "
                        + "for details.",
                type = TemplateProperty.PropertyType.Text,
                feel = Property.FeelMode.required,
                optional = true)
            List<ToolCallResult> toolCallResults) {}

    public record MemoryConfiguration(
        @Valid @NestedProperties(group = "memory") MemoryStorageConfiguration storage,
        // TODO support more advanced eviction policies (token window)
        @TemplateProperty(
                group = "memory",
                label = "Context window size",
                description =
                    "Maximum number of recent conversation messages which are passed to the model.",
                tooltip =
                    "Use this to limit the number of messages which are sent to the model. The agent will only send "
                        + "the most recent messages up to the configured limit to the LLM. Older messages will be kept "
                        + "in the conversation store, but not sent to the model. "
                        + "<a href=\"https://docs.camunda.io/docs/8.8/components/connectors/out-of-the-box-connectors/agentic-ai-aiagent/\" target=\"_blank\">See documentation</a> "
                        + "for details.",
                type = TemplateProperty.PropertyType.Number,
                defaultValue = "20",
                defaultValueType = TemplateProperty.DefaultValueType.Number)
            @Min(3)
            Integer contextWindowSize) {}

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
            @Min(1)
            Integer maxModelCalls) {}
  }
}
