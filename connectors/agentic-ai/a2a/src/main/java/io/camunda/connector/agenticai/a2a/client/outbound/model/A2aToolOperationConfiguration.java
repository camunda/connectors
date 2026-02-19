/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.outbound.model;

import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record A2aToolOperationConfiguration(
    @NotBlank
        @FEEL
        @TemplateProperty(
            group = "operation",
            label = "Operation",
            description =
                "The action to perform. Possible values are <code>fetchAgentCard</code> and <code>sendMessage</code>.",
            defaultValue = "=toolCall.operation",
            type = TemplateProperty.PropertyType.String,
            feel = FeelMode.required)
        String operation,
    @FEEL
        @TemplateProperty(
            group = "operation",
            label = "Parameters",
            description =
                "The parameters used to build the message that will be sent to the remote agent.",
            defaultValue = "=toolCall.params",
            type = TemplateProperty.PropertyType.Text,
            feel = FeelMode.required,
            optional = true)
        Map<String, Object> params,
    @Valid @NotNull A2aCommonSendMessageConfiguration sendMessageSettings) {}
