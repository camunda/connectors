/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request;

import io.camunda.connector.agenticai.aiagent.model.tool.DocumentDelivery;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.jspecify.annotations.Nullable;

public interface PromptConfiguration {
  String prompt();

  record SystemPromptConfiguration(
      @FEEL
          @TemplateProperty(
              group = "systemPrompt",
              label = "System prompt",
              type = TemplateProperty.PropertyType.Text,
              feel = FeelMode.required,
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
              defaultValue = DEFAULT_SYSTEM_PROMPT)
          String prompt)
      implements PromptConfiguration {

    @TemplateProperty(ignore = true)
    public static final String DEFAULT_SYSTEM_PROMPT =
"""
="You are **TaskAgent**, a helpful, capable agent that can handle a wide variety of customer requests using your own knowledge **and** any tools explicitly provided to you at runtime. Prefer a provided tool over guessing whenever it would give a more accurate or reliable answer, and only use tools that were explicitly configured for you (never assume a tool exists). You can call the same tool multiple times with different inputs if that helps complete the request. If no tool fits the request, answer from your own knowledge; if you can't produce a reliable answer, say so plainly and explain why, rather than guessing.\"""";
  }

  record UserPromptConfiguration(
      @NotBlank
          @FEEL
          @TemplateProperty(
              group = "userPrompt",
              label = "User prompt",
              type = TemplateProperty.PropertyType.Text,
              feel = FeelMode.required,
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String prompt,
      @FEEL
          @TemplateProperty(
              group = "userPrompt",
              label = "Documents",
              description = "Documents to be included in the user prompt.",
              tooltip =
                  "Referenced documents will be automatically added to the user prompt. "
                      + "<a href=\"https://docs.camunda.io/docs/8.10/components/connectors/out-of-the-box-connectors/agentic-ai-aiagent-task/\" target=\"_blank\">See documentation</a> "
                      + "for details and supported file types.",
              feel = FeelMode.required,
              optional = true)
          @Nullable List<Document> documents,
      @TemplateProperty(
              group = "userPrompt",
              label = "Document delivery",
              description =
                  "How the documents of the user prompt reach the model: as references or with their content.",
              tooltip =
                  "<code>Reference</code>: the model sees a reference to each document and loads the content it needs with a tool. "
                      + "<code>Content</code>: the content of text, PDF and image documents is part of the conversation right away. "
                      + "Large documents cost many tokens in every model call; a document that is too large or of an unsupported type stays a reference.",
              type = TemplateProperty.PropertyType.Dropdown,
              choices = {
                @TemplateProperty.DropdownPropertyChoice(value = "reference", label = "Reference"),
                @TemplateProperty.DropdownPropertyChoice(value = "content", label = "Content")
              },
              defaultValue = "reference")
          @Nullable DocumentDelivery documentDelivery)
      implements PromptConfiguration {

    public UserPromptConfiguration(String prompt, @Nullable List<Document> documents) {
      this(prompt, documents, null);
    }
  }
}
