/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.model;

import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Duration;

public record A2aCommonSendMessageConfiguration(
    @PositiveOrZero
        @TemplateProperty(
            group = "operation",
            label = "History length",
            description =
                "The number of most recent messages from the task's history to retrieve in the response.",
            feel = Property.FeelMode.optional,
            defaultValueType = TemplateProperty.DefaultValueType.Number,
            defaultValue = "3")
        Integer historyLength,
    @TemplateProperty(
            group = "operation",
            label = "Response timeout",
            description =
                "How long to wait for the remote agent response as ISO-8601 duration (example: <code>PT1M</code>).",
            defaultValue = "PT1M")
        Duration timeout) {}
