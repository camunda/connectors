/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.appintegrations.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotEmpty;

/**
 * What is sent: plain text, a hand-written adaptive card, or a Camunda form the backend renders as
 * an adaptive card. The sealed hierarchy makes the three mutually exclusive by construction, so no
 * cross-field validation is needed.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = MessageContent.TextContent.class, name = "text"),
  @JsonSubTypes.Type(value = MessageContent.AdaptiveCardContent.class, name = "adaptiveCard"),
  @JsonSubTypes.Type(value = MessageContent.FormContent.class, name = "form")
})
@TemplateDiscriminatorProperty(
    name = "type",
    group = "message",
    label = "Message type",
    description =
        "Send plain text, a custom adaptive card, or a Camunda form rendered as an adaptive card",
    defaultValue = "text")
public sealed interface MessageContent {

  @TemplateSubType(id = TextContent.TYPE, label = "Text message")
  record TextContent(
      @NotEmpty
          @TemplateProperty(
              group = "message",
              label = "Message",
              description = "Plain text content to send.",
              type = PropertyType.Text)
          String message)
      implements MessageContent {

    @TemplateProperty(ignore = true)
    public static final String TYPE = "text";
  }

  @TemplateSubType(id = AdaptiveCardContent.TYPE, label = "Adaptive card")
  record AdaptiveCardContent(
      @NotEmpty
          @TemplateProperty(
              group = "message",
              label = "Adaptive card JSON",
              description = "JSON payload for a custom Teams adaptive card.",
              type = PropertyType.Text)
          String adaptiveCardJson)
      implements MessageContent {

    @TemplateProperty(ignore = true)
    public static final String TYPE = "adaptiveCard";
  }

  /**
   * Carries no variables of its own: the form is supplied as a {@code zeebe:linkedResource}
   * declared by {@code @TemplateLinkedResource} on {@link SendMessageRequest} and delivered to the
   * connector in the job's {@code linkedResources} custom header. This subtype exists to make the
   * form one of the message-type choices and to gate those linked-resource properties in the
   * element template.
   */
  @TemplateSubType(id = FormContent.TYPE, label = "Form")
  record FormContent() implements MessageContent {

    @TemplateProperty(ignore = true)
    public static final String TYPE = "form";
  }
}
