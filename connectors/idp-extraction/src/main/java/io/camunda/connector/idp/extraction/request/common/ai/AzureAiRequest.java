/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.request.common.ai;

import static io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType.Dropdown;

import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotNull;

@TemplateSubType(id = "azureAiFoundry", label = "Azure AI Foundry")
public record AzureAiRequest(
    @TemplateProperty(
            id = "usingOpenAI",
            label = "Model type",
            group = "ai",
            type = Dropdown,
            description = "Specify if the Azure AI Foundry is using OpenAI",
            defaultValue = "false",
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            choices = {
              @TemplateProperty.DropdownPropertyChoice(
                  label = "Base Azure Foundry model",
                  value = "false"),
              @TemplateProperty.DropdownPropertyChoice(label = "Azure OpenAI model", value = "true")
            })
        boolean usingOpenAI,
    @TemplateProperty(
            id = "endpoint",
            label = "Azure AI Endpoint",
            group = "ai",
            type = TemplateProperty.PropertyType.Text,
            description = "Specify the endpoint of Azure AI",
            binding = @TemplateProperty.PropertyBinding(name = "endpoint"),
            feel = FeelMode.disabled,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        @NotNull
        String endpoint,
    @TemplateProperty(
            id = "apiKey",
            label = "Azure AI API Key",
            group = "ai",
            type = TemplateProperty.PropertyType.Text,
            description = "Specify the API key of Azure AI",
            binding = @TemplateProperty.PropertyBinding(name = "apiKey"),
            feel = FeelMode.disabled,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        @NotNull
        String apiKey)
    implements AiProvider {}
