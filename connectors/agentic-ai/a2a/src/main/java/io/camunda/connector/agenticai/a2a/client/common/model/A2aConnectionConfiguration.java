/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.common.model;

import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotBlank;

public record A2aConnectionConfiguration(
    @NotBlank
        @FEEL
        @TemplateProperty(
            group = "connection",
            label = "A2A server URL",
            description = "The base URL of the A2A server.",
            type = TemplateProperty.PropertyType.String,
            feel = FeelMode.optional)
        String url,
    @FEEL
        @TemplateProperty(
            group = "connection",
            label = "Agent card location",
            description =
                "Optional path to the agent card endpoint relative to the base server URL, defaults to <code>.well-known/agent-card.json</code>.",
            type = TemplateProperty.PropertyType.String,
            feel = FeelMode.optional,
            optional = true)
        String agentCardLocation) {}
