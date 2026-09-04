/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.connector.agenticai.aiagent.model.request.v2.ProviderConfiguration;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record AgentTaskV2Request(
    @Valid @NotNull ProviderConfiguration provider,
    @Valid @NotNull AgentTaskRequestData data,
    @JsonIgnore
        @TemplateProperty(
            id = "promptCaching.openai.status",
            binding = @TemplateProperty.PropertyBinding(name = "modeler.promptCachingOpenAI"),
            group = "model",
            label = "Prompt caching",
            description = "Automatic.",
            tooltip =
                "Can speed up responses and lower API costs by reusing text from recent requests. Best for long conversations or large documents."
                    + "<br><br>See the <a href=\"https://developers.openai.com/api/docs/guides/prompt-caching\" target=\"_blank\">caching documentation</a>.",
            condition =
                @TemplateProperty.PropertyCondition(property = "provider.type", equals = "openai"),
            type = TemplateProperty.PropertyType.Boolean,
            defaultValue = "true",
            defaultValueType = TemplateProperty.DefaultValueType.Boolean)
        boolean openAiPromptCachingStatus,
    @JsonIgnore
        @TemplateProperty(
            id = "promptCaching.googleGemini.status",
            binding = @TemplateProperty.PropertyBinding(name = "modeler.promptCachingGoogleGemini"),
            group = "model",
            label = "Prompt caching",
            description = "Automatic.",
            tooltip =
                "Can speed up responses and lower API costs by reusing text from recent requests. Best for long conversations or large documents."
                    + "<br><br>See the <a href=\"https://ai.google.dev/gemini-api/docs/caching\" target=\"_blank\">caching documentation</a>.",
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "provider.type",
                    equals = "google-gemini"),
            type = TemplateProperty.PropertyType.Boolean,
            defaultValue = "true",
            defaultValueType = TemplateProperty.DefaultValueType.Boolean)
        boolean googleGeminiPromptCachingStatus,
    @JsonIgnore
        @TemplateProperty(
            id = "promptCaching.custom.status",
            binding = @TemplateProperty.PropertyBinding(name = "modeler.promptCachingCustom"),
            group = "model",
            label = "Prompt caching",
            description = "Not available.",
            tooltip =
                "The prompt caching property does not control caching for custom implementations. Use a custom solution instead.",
            condition =
                @TemplateProperty.PropertyCondition(property = "provider.type", equals = "custom"),
            type = TemplateProperty.PropertyType.Boolean,
            defaultValue = "false",
            defaultValueType = TemplateProperty.DefaultValueType.Boolean)
        boolean customPromptCachingStatus) {

  public AgentTaskV2Request(ProviderConfiguration provider, AgentTaskRequestData data) {
    this(provider, data, true, true, false);
  }
}
