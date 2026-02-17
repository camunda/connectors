/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.request.common.ai;

import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

@TemplateSubType(id = "openAi", label = "OpenAi Compatible")
public record OpenAiRequest(
    @TemplateProperty(
            id = "openAiEndpoint",
            label = "OpenAI Spec Endpoint",
            group = "ai",
            type = TemplateProperty.PropertyType.Text,
            description = "Specify the OpenAI compatible specification endpoint.",
            binding = @TemplateProperty.PropertyBinding(name = "openAiEndpoint"),
            feel = FeelMode.disabled,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        @NotNull
        String openAiEndpoint,
    @FEEL
        @TemplateProperty(
            id = "openAiHeaders",
            label = "Headers",
            group = "ai",
            description = "Map of HTTP headers to add to the request.",
            feel = FeelMode.disabled,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        @NotNull
        Map<String, String> openAiHeaders)
    implements AiProvider {}
