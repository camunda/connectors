/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.outbound.model;

import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.kafka.model.KafkaAuthentication;
import io.camunda.connector.kafka.model.KafkaTopic;
import io.camunda.connector.kafka.model.schema.NoSchemaStrategy;
import io.camunda.connector.kafka.model.schema.OutboundSchemaStrategy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.Optional;

public record KafkaConnectorRequest(
    @Valid KafkaAuthentication authentication,
    @Valid @NotNull KafkaTopic topic,
    @Valid @NotNull KafkaMessage message,
    @Valid OutboundSchemaStrategy schemaStrategy,
    @TemplateProperty(
            group = "message",
            label = "Headers",
            optional = true,
            feel = FeelMode.required,
            description = "Provide Kafka producer headers in JSON")
        Map<String, String> headers,
    @TemplateProperty(
            group = "kafka",
            label = "Additional properties",
            optional = true,
            feel = FeelMode.required,
            description = "Provide additional Kafka producer properties in JSON")
        Map<String, Object> additionalProperties) {
  @Override
  public @Valid OutboundSchemaStrategy schemaStrategy() {
    return Optional.ofNullable(schemaStrategy).orElse(new NoSchemaStrategy());
  }
}
