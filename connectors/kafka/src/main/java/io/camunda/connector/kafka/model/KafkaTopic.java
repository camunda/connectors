/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.model;

import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.NullableBoolean;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyCondition;
import jakarta.validation.constraints.NotEmpty;

public record KafkaTopic(
    @FEEL
        @TemplateProperty(
            group = "authentication",
            label = "Bootstrap servers",
            placeholder = "broker1:9092,broker2:9092",
            condition =
                @PropertyCondition(
                    property = "kafkaConnectionConfiguration",
                    isEmpty = NullableBoolean.TRUE),
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            tooltip = "Bootstrap server(s), comma-delimited if there are multiple.")
        String bootstrapServers,
    @NotEmpty @TemplateProperty(label = "Topic", group = "kafka") String topicName) {}
