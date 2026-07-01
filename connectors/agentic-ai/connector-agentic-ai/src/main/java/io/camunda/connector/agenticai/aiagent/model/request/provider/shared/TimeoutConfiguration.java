/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.provider.shared;

import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import java.time.Duration;

public record TimeoutConfiguration(
    @TemplateProperty(
            group = "provider",
            label = "Timeout",
            description =
                "Timeout specification for API calls to the model provider defined as ISO-8601 duration (example: <code>PT60S</code>).",
            type = TemplateProperty.PropertyType.String,
            feel = FeelMode.optional,
            optional = true,
            defaultValueType = TemplateProperty.DefaultValueType.String)
        Duration timeout) {}
