/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.outbound.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.DropdownItem;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Duration;

public record A2aCommonSendMessageConfiguration(
    @PositiveOrZero
        @TemplateProperty(
            group = "operation",
            label = "History length",
            description =
                "The number of most recent messages from the task's history to retrieve in the response.",
            feel = Property.FeelMode.optional,
            defaultValueType = TemplateProperty.DefaultValueType.Number,
            defaultValue = "3")
        Integer historyLength,
    @Valid @NotNull
        A2aCommonSendMessageConfiguration.A2aResponseRetrievalMode responseRetrievalMode,
    @TemplateProperty(
            group = "operation",
            label = "Response timeout",
            description =
                "How long to wait for the remote agent response as ISO-8601 duration (example: <code>PT1M</code>).",
            defaultValue = "PT1M")
        Duration timeout) {

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(
        value = A2aResponseRetrievalMode.Blocking.class,
        name = A2aResponseRetrievalMode.Blocking.BLOCKING_ID),
    @JsonSubTypes.Type(
        value = A2aResponseRetrievalMode.Polling.class,
        name = A2aResponseRetrievalMode.Polling.POLLING_ID),
    @JsonSubTypes.Type(
        value = A2aResponseRetrievalMode.Notification.class,
        name = A2aResponseRetrievalMode.Notification.NOTIFICATION_ID)
  })
  @TemplateDiscriminatorProperty(
      group = "operation",
      label = "Response retrieval mode",
      name = "type",
      description = "How to receive the final response from the remote agent.",
      defaultValue = A2aResponseRetrievalMode.Polling.POLLING_ID)
  public sealed interface A2aResponseRetrievalMode {
    @TemplateSubType(id = Blocking.BLOCKING_ID, label = "Blocking")
    record Blocking() implements A2aResponseRetrievalMode {
      @TemplateProperty(ignore = true)
      public static final String BLOCKING_ID = "blocking";
    }

    @TemplateSubType(id = Polling.POLLING_ID, label = "Polling")
    record Polling() implements A2aResponseRetrievalMode {
      @TemplateProperty(ignore = true)
      public static final String POLLING_ID = "polling";
    }

    @TemplateSubType(id = Notification.NOTIFICATION_ID, label = "Notification")
    record Notification(
        @NotBlank
            @TemplateProperty(
                group = "operation",
                label = "Webhook URL",
                description = "The webhook URL where the remote agent will send the response.",
                feel = Property.FeelMode.optional)
            String webhookUrl,
        @NotNull
            @TemplateProperty(
                group = "operation",
                label = "Authorization type",
                description = "The authorization type required by the webhook.",
                type = TemplateProperty.PropertyType.Dropdown)
            AuthorizationType authorizationType)
        implements A2aResponseRetrievalMode {
      @TemplateProperty(ignore = true)
      public static final String NOTIFICATION_ID = "notification";

      public enum AuthorizationType {
        @DropdownItem(label = "None")
        NONE,
        @DropdownItem(label = "Basic")
        BASIC,
        @DropdownItem(label = "API key")
        API_KEY,
        @DropdownItem(label = "Bearer token")
        BEARER_TOKEN;

        public String toA2aSecurityScheme() {
          return switch (this) {
            case NONE -> null;
            case BASIC -> "Basic";
            case API_KEY -> "ApiKey";
            case BEARER_TOKEN -> "Bearer";
          };
        }
      }
    }
  }
}
