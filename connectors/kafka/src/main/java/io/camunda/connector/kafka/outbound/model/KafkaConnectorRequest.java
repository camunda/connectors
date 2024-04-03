/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.outbound.model;

import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.kafka.model.Avro;
import io.camunda.connector.kafka.model.KafkaAuthentication;
import io.camunda.connector.kafka.model.KafkaTopic;
import io.camunda.connector.kafka.model.SerializationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record KafkaConnectorRequest(
    @TemplateProperty(
            group = "kafka",
            label = "Serialization type",
            id = "serializationType",
            defaultValue = "json",
            type = TemplateProperty.PropertyType.Dropdown,
            choices = {
              @TemplateProperty.DropdownPropertyChoice(value = "json", label = "Default (JSON)"),
              @TemplateProperty.DropdownPropertyChoice(
                  value = "avro",
                  label = "Avro (experimental)")
            },
            description =
                "Select the serialization type. For details, visit the <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/kafka/?kafka=outbound\" target=\"_blank\">documentation</a>")
        SerializationType serializationType,
    @Valid KafkaAuthentication authentication,
    @Valid @NotNull KafkaTopic topic,
    @Valid @NotNull KafkaMessage message,
    @Valid Avro avro,
    @TemplateProperty(
            group = "kafka",
            label = "Headers",
            optional = true,
            feel = Property.FeelMode.required,
            description = "Provide Kafka producer headers in JSON")
        Map<String, String> headers,
    @TemplateProperty(
            group = "kafka",
            label = "Additional properties",
            optional = true,
            feel = Property.FeelMode.required,
            description = "Provide additional Kafka producer properties in JSON")
        Map<String, Object> additionalProperties) {}
