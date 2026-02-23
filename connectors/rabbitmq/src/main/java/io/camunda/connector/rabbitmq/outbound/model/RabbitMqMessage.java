/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.outbound.model;

import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotNull;

public record RabbitMqMessage(
    @TemplateProperty(
            group = "message",
            feel = FeelMode.required,
            optional = true,
            defaultValue = "={}",
            type = TemplateProperty.PropertyType.Text,
            description = "Properties for the message, routing headers, etc")
        Object properties,
    @NotNull
        @TemplateProperty(
            group = "message",
            label = "Message",
            type = TemplateProperty.PropertyType.Text,
            description = "Data to send to RabbitMQ")
        Object body) {}
