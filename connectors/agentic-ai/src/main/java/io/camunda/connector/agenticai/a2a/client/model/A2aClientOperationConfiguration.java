/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.model;

import static io.camunda.connector.agenticai.a2a.client.model.A2aClientOperationConfiguration.FetchAgentCardOperationConfiguration.FETCH_AGENT_CARD_ID;
import static io.camunda.connector.agenticai.a2a.client.model.A2aClientOperationConfiguration.SendMessageOperationConfiguration.SEND_MESSAGE_ID;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(
      value = A2aClientOperationConfiguration.FetchAgentCardOperationConfiguration.class,
      name = FETCH_AGENT_CARD_ID),
  @JsonSubTypes.Type(
      value = A2aClientOperationConfiguration.SendMessageOperationConfiguration.class,
      name = SEND_MESSAGE_ID)
})
@TemplateDiscriminatorProperty(
    group = "operation",
    label = "Operation",
    name = "type",
    description = "The type of operation to perform.",
    defaultValue = FETCH_AGENT_CARD_ID)
public sealed interface A2aClientOperationConfiguration
    permits A2aClientOperationConfiguration.FetchAgentCardOperationConfiguration,
        A2aClientOperationConfiguration.SendMessageOperationConfiguration {

  @TemplateSubType(id = FETCH_AGENT_CARD_ID, label = "Fetch Agent Card")
  record FetchAgentCardOperationConfiguration() implements A2aClientOperationConfiguration {

    @TemplateProperty(ignore = true)
    public static final String FETCH_AGENT_CARD_ID = "fetchAgentCard";
  }

  @TemplateSubType(id = SEND_MESSAGE_ID, label = "Send message")
  record SendMessageOperationConfiguration(
      @Valid @NotNull Parameters params,
      @TemplateProperty(
              group = "operation",
              label = "Response timeout",
              description =
                  "How long to wait for the remote agent response as ISO-8601 duration (example: <code>PT1M</code>).",
              defaultValue = "PT1M")
          Duration timeout)
      implements A2aClientOperationConfiguration {

    @TemplateProperty(ignore = true)
    public static final String SEND_MESSAGE_ID = "sendMessage";

    public record Parameters(
        @NotBlank
            @FEEL
            @TemplateProperty(
                group = "operation",
                label = "Text",
                description = "The text to to be included in the message.",
                type = TemplateProperty.PropertyType.Text,
                feel = Property.FeelMode.optional)
            String text,
        @FEEL
            @TemplateProperty(
                group = "operation",
                label = "Documents",
                description = "Documents to be included in the message.",
                // TODO: add link to documentation
                tooltip = "Referenced documents that will be added to the message.",
                feel = Property.FeelMode.required,
                optional = true)
            List<Document> documents) {}
  }
}
