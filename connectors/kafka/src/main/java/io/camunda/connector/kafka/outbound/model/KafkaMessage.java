/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.outbound.model;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotNull;

public record KafkaMessage(
    @NotNull
        @TemplateProperty(group = "message", label = "Key", description = "Provide message key")
        Object key,
    @NotNull
        @TemplateProperty(group = "message", label = "Value", description = "Provide message value")
        Object value) {}
