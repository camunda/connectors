/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.inbound;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.connector.feel.annotation.FEEL;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.kafka.model.Avro;
import io.camunda.connector.kafka.model.KafkaAuthentication;
import io.camunda.connector.kafka.model.KafkaTopic;
import io.camunda.connector.kafka.model.SerializationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

public record KafkaConnectorProperties(
    @TemplateProperty(
            group = "kafka",
            label = "Deserialization type",
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
                "Select the deserialization type. For details, visit the <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/kafka/?kafka=inbound\" target=\"_blank\">documentation</a>")
        SerializationType serializationType,
    @NotNull
        @TemplateProperty(
            group = "authentication",
            label = "Authentication type",
            defaultValue = "credentials",
            type = TemplateProperty.PropertyType.Dropdown,
            choices = {
              @TemplateProperty.DropdownPropertyChoice(
                  value = "credentials",
                  label = "Credentials"),
              @TemplateProperty.DropdownPropertyChoice(value = "custom", label = "Custom")
            },
            description = "Username/password or custom")
        AuthenticationType authenticationType,
    @Valid KafkaAuthentication authentication,
    @Valid @NotNull KafkaTopic topic,
    @TemplateProperty(
            group = "kafka",
            label = "Consumer Group ID",
            feel = Property.FeelMode.disabled,
            description =
                "Provide the consumer group ID used by the connector. Leave empty for an automatically generated one")
        String groupId,
    @FEEL
        @TemplateProperty(
            group = "kafka",
            label = "Additional properties",
            optional = true,
            feel = Property.FeelMode.required,
            description = "Provide additional Kafka consumer properties in JSON")
        Map<String, Object> additionalProperties,
    @FEEL
        @TemplateProperty(
            group = "kafka",
            label = "Offsets",
            feel = Property.FeelMode.optional,
            description =
                "List of offsets, e.g. '10' or '=[10, 23]'. If specified, it has to have the same number of values as the number of partitions")
        List<Long> offsets,
    @NotNull
        @TemplateProperty(
            group = "kafka",
            label = "Auto offset reset",
            defaultValue = "latest",
            type = TemplateProperty.PropertyType.Dropdown,
            choices = {
              @TemplateProperty.DropdownPropertyChoice(value = "none", label = "None"),
              @TemplateProperty.DropdownPropertyChoice(value = "latest", label = "Latest"),
              @TemplateProperty.DropdownPropertyChoice(value = "earliest", label = "Earliest")
            },
            description =
                "What to do when there is no initial offset in Kafka or if the current offset does not exist any more on the server. You should only select none if you specified the offsets")
        AutoOffsetReset autoOffsetReset, // = AutoOffsetReset.NONE;
    @Valid Avro avro) {

  public enum AutoOffsetReset {
    @JsonProperty("none")
    NONE("none"),
    @JsonProperty("latest")
    LATEST("latest"),
    @JsonProperty("earliest")
    EARLIEST("earliest");

    public final String label;

    AutoOffsetReset(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  public enum AuthenticationType {
    credentials,
    custom
  }
}
