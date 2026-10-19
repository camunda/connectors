/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.inbound;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.NestedProperties;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.NullableBoolean;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyCondition;
import io.camunda.connector.kafka.model.KafkaAuthentication;
import io.camunda.connector.kafka.model.KafkaConnectionConfiguration;
import io.camunda.connector.kafka.model.KafkaTopic;
import io.camunda.connector.kafka.model.schema.InboundSchemaStrategy;
import io.camunda.connector.kafka.model.schema.NoSchemaStrategy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record KafkaConnectorProperties(
    @FEEL
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
    @TemplateProperty(
            group = "authentication",
            label = "Authentication type",
            defaultValue = "credentials",
            condition =
                @PropertyCondition(
                    property = "kafkaConnectionConfiguration",
                    isEmpty = NullableBoolean.TRUE),
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            type = TemplateProperty.PropertyType.Dropdown,
            choices = {
              @TemplateProperty.DropdownPropertyChoice(
                  value = "credentials",
                  label = "Credentials"),
              @TemplateProperty.DropdownPropertyChoice(value = "custom", label = "Custom")
            },
            tooltip = "Username/password or custom.")
        AuthenticationType authenticationType,
    @Valid
        @NestedProperties(
            condition =
                @PropertyCondition(
                    property = "kafkaConnectionConfiguration",
                    isEmpty = NullableBoolean.TRUE))
        KafkaAuthentication authentication,
    @Valid @NotNull KafkaTopic topic,
    @TemplateProperty(
            group = "kafka",
            label = "Consumer group ID",
            tooltip =
                "It is strongly recommended to provide an explicit consumer group ID. "
                    + "Use a stable, application-specific identifier that represents the logical consumer group in your application "
                    + "(for example, <code>my-app-order-processor</code>). "
                    + "Leaving this empty auto-generates an ID that may change across connector upgrades, causing message replay.")
        String groupId,
    @FEEL
        @TemplateProperty(
            group = "kafka",
            label = "Additional properties",
            optional = true,
            feel = FeelMode.required,
            tooltip =
                "Additional Kafka consumer properties in JSON. These can override brokers and authentication from a reusable credential.")
        Map<String, Object> additionalProperties,
    @FEEL
        @TemplateProperty(
            group = "kafka",
            label = "Offsets",
            feel = FeelMode.optional,
            optional = true,
            tooltip =
                "List of offsets, e.g. '10' or '=[10, 23]'. If specified, it has to have the same number of values as the number of partitions.")
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
            tooltip =
                "What to do when there is no initial offset in Kafka or if the current offset does not exist any more on the server. You should only select none if you specified the offsets.")
        AutoOffsetReset autoOffsetReset, // = AutoOffsetReset.NONE;
    @Valid InboundSchemaStrategy schemaStrategy) {

  public KafkaConnectorProperties(
      AuthenticationType authenticationType,
      KafkaAuthentication authentication,
      KafkaTopic topic,
      String groupId,
      Map<String, Object> additionalProperties,
      List<Long> offsets,
      AutoOffsetReset autoOffsetReset,
      InboundSchemaStrategy schemaStrategy) {
    this(
        null,
        authenticationType,
        authentication,
        topic,
        groupId,
        additionalProperties,
        offsets,
        autoOffsetReset,
        schemaStrategy);
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

  @AssertTrue(message = "An authentication type or a Kafka connection credential must be provided")
  @JsonIgnore
  public boolean isAuthenticationTypePresent() {
    return kafkaConnectionConfiguration != null || authenticationType != null;
  }

  @Override
  public @Valid InboundSchemaStrategy schemaStrategy() {
    return Optional.ofNullable(schemaStrategy).orElse(new NoSchemaStrategy());
  }

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
