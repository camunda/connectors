/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.outbound.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.NestedProperties;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.NullableBoolean;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyCondition;
import io.camunda.connector.kafka.model.KafkaAuthentication;
import io.camunda.connector.kafka.model.KafkaConnectionConfiguration;
import io.camunda.connector.kafka.model.KafkaTopic;
import io.camunda.connector.kafka.model.schema.NoSchemaStrategy;
import io.camunda.connector.kafka.model.schema.OutboundSchemaStrategy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.Optional;

public record KafkaConnectorRequest(
    @Valid
        @TemplateProperty(
            id = "kafkaConnectionConfiguration",
            label = "Connection credential",
            group = "authentication",
            type = TemplateProperty.PropertyType.Configuration,
            optional = true,
            tooltip =
                "Choose a reusable Kafka connection credential, or configure one-time connection parameters below.")
        KafkaConnectionConfiguration kafkaConnectionConfiguration,
    @Valid
        @NestedProperties(
            condition =
                @PropertyCondition(
                    property = "kafkaConnectionConfiguration",
                    isEmpty = NullableBoolean.TRUE))
        KafkaAuthentication authentication,
    @Valid @NotNull KafkaTopic topic,
    @Valid @NotNull KafkaMessage message,
    @Valid OutboundSchemaStrategy schemaStrategy,
    @TemplateProperty(
            group = "message",
            label = "Headers",
            optional = true,
            feel = FeelMode.required,
            tooltip = "Kafka producer headers in JSON.")
        Map<String, String> headers,
    @TemplateProperty(
            group = "kafka",
            label = "Additional properties",
            optional = true,
            feel = FeelMode.required,
            tooltip =
                "Additional Kafka producer properties in JSON. These override generated properties, including brokers and authentication from a reusable credential.")
        Map<String, Object> additionalProperties) {

  public KafkaConnectorRequest(
      KafkaAuthentication authentication,
      KafkaTopic topic,
      KafkaMessage message,
      OutboundSchemaStrategy schemaStrategy,
      Map<String, String> headers,
      Map<String, Object> additionalProperties) {
    this(null, authentication, topic, message, schemaStrategy, headers, additionalProperties);
  }

  public KafkaAuthentication authentication() {
    return kafkaConnectionConfiguration != null
        ? kafkaConnectionConfiguration.toAuthentication()
        : authentication;
  }

  public KafkaTopic topic() {
    return kafkaConnectionConfiguration != null && topic != null
        ? new KafkaTopic(kafkaConnectionConfiguration.bootstrapServers(), topic.topicName())
        : topic;
  }

  @AssertTrue(message = "No bootstrap servers provided by the credential or the element template")
  @JsonIgnore
  public boolean isBootstrapServersPresent() {
    var effectiveTopic = topic();
    return effectiveTopic != null
        && effectiveTopic.bootstrapServers() != null
        && !effectiveTopic.bootstrapServers().isEmpty();
  }

  @Override
  public @Valid OutboundSchemaStrategy schemaStrategy() {
    return Optional.ofNullable(schemaStrategy).orElse(new NoSchemaStrategy());
  }
}
