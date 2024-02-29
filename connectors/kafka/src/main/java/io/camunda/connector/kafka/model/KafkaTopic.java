/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.model;

import io.camunda.connector.feel.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotEmpty;

public record KafkaTopic(
    @FEEL
        @NotEmpty
        @TemplateProperty(
            group = "kafka",
            label = "Bootstrap servers",
            description = "Provide bootstrap server(s), comma-delimited if there are multiple")
        String bootstrapServers,
    @NotEmpty
        @TemplateProperty(label = "Topic", group = "kafka", description = "Provide topic name")
        String topicName) {}
