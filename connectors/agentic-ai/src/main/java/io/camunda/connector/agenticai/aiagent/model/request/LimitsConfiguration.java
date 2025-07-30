/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.Min;

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
