/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.model;

import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyBinding;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record ConverseData(
    @TemplateProperty(
            label = "Model ID",
            group = "converse",
            description =
                "Specify the model ID. Details in the <a href=\"https://docs.aws.amazon.com/bedrock/latest/userguide/model-ids.html\" target=\"_blank\">documentation</a>",
            id = "converseData.modelId",
            binding = @PropertyBinding(name = "converseData.modelId"))
        @Valid
        @NotNull
        String modelId,
    @TemplateProperty(
            label = "Max token returned",
            group = "converse",
            id = "converseData.maxTokens",
            feel = Property.FeelMode.optional,
            optional = true,
            defaultValue = "512",
            binding = @PropertyBinding(name = "converseData.maxTokens"))
        Integer maxTokens,
    @TemplateProperty(
            label = "Temperature",
            group = "converse",
            id = "converseData.temperature",
            feel = Property.FeelMode.optional,
            optional = true,
            defaultValue = "0.5",
            binding = @PropertyBinding(name = "converseData.temperature"))
        Float temperature,
    @TemplateProperty(
            label = "top P",
            group = "converse",
            id = "converseData.topP",
            feel = Property.FeelMode.optional,
            optional = true,
            defaultValue = "0.9",
            binding = @PropertyBinding(name = "converseData.topP"))
        Float topP) {}
