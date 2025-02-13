/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.message;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyCondition;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.util.Map;

public record SendMessageRequest(
    @TemplateProperty(
            id = "mode",
            defaultValue = "publish",
            type = TemplateProperty.PropertyType.Dropdown,
            choices = {
              @TemplateProperty.DropdownPropertyChoice(label = "publish message", value = "publish")
              //              ,
              //              @TemplateProperty.DropdownPropertyChoice(
              //                  label = "correlate message",
              //                  value = "correlate")
            })
        String mode,
    @NotBlank String messageName,
    @TemplateProperty(optional = true) String correlationKey,
    @TemplateProperty(optional = true, label = "Payload") Map<String, Object> variables,
    @TemplateProperty(
            condition = @PropertyCondition(property = "mode", equals = "publish"),
            optional = true)
        Duration timeToLive,
    @TemplateProperty(
            condition = @PropertyCondition(property = "mode", equals = "publish"),
            optional = true)
        String messageId,
    @TemplateProperty(optional = true) String tenantId,
    @TemplateProperty(optional = true) Duration requestTimeout) {}
