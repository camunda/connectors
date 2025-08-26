/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.feel.annotation.FEEL;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Map;

public interface PromptConfiguration {
  String PROMPT_PARAMETERS_DESCRIPTION =
      "Use <code>{{parameter}}</code> format in the prompt to insert values defined in this map.";

  String prompt();

  Map<String, Object> parameters();

  record SystemPromptConfiguration(
      @FEEL
          @TemplateProperty(
              group = "systemPrompt",
              label = "System prompt",
              type = TemplateProperty.PropertyType.Text,
              feel = Property.FeelMode.optional,
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
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

  record UserPromptConfiguration(
      @FEEL
          @TemplateProperty(
              group = "userPrompt",
              label = "User prompt",
              type = TemplateProperty.PropertyType.Text,
              feel = Property.FeelMode.optional,
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
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
}
