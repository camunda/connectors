/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.provider.mixin;

import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;

public record TimeoutConfiguration(
    @TemplateProperty(
            group = "provider",
            label = "Timeout",
            description =
                "Timeout specification for API calls to the model provider in ISO-8601 duration format (e.g., <code>PT60S</code> for 60 seconds).",
            type = TemplateProperty.PropertyType.String,
            feel = Property.FeelMode.optional,
            defaultValue = "PT60S",
            defaultValueType = TemplateProperty.DefaultValueType.String)
        @NotNull
        Duration timeout) {}
