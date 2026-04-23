/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.appintegrations.model;

import io.camunda.connector.generator.java.annotation.TemplateLinkedResource;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

@TemplateLinkedResource(
    linkName = "formDefinition",
    resourceType = "form",
    group = "form",
    resourceIdLabel = "Form ID",
    resourceIdDescription =
        "Optional. ID of the Camunda form to render as an adaptive card in the Teams message. Leave blank to send a plain text message or adaptive card.",
    bindingTypeLabel = "Form binding")
public record SendMessageRequest(
    @NotNull @Valid AppIntegrationsConfiguration configuration,
    @TemplateProperty(
            group = "message",
            label = "User Email",
            description =
                "Email address of the recipient. Use a FEEL expression to reference a process variable, e.g. =assigneeEmail.",
            optional = true)
        String email,
    @TemplateProperty(
            group = "message",
            label = "Channel ID",
            description =
                "Microsoft Teams channel ID to send to directly, e.g. 19:xxx@thread.tacv2. Use when sending to a channel rather than a user.",
            optional = true)
        String channelId,
    @TemplateProperty(
            group = "message",
            label = "Message",
            description =
                "Plain text content to send. Provide either this, an adaptive card, or link a form.",
            type = PropertyType.Text,
            optional = true)
        String message,
    @TemplateProperty(
            group = "message",
            label = "Adaptive card",
            description =
                "JSON payload for a custom Teams adaptive card. Provide either this, a plain text message, or link a form.",
            type = PropertyType.Text,
            optional = true)
        String adaptiveCardJson) {

  @AssertTrue(message = "Exactly one of 'email' or 'channelId' must be provided")
  public boolean isRecipientValid() {
    boolean hasEmail = email != null && !email.isBlank();
    boolean hasChannelId = channelId != null && !channelId.isBlank();
    return hasEmail ^ hasChannelId;
  }

  @AssertTrue(message = "'message' and 'adaptiveCardJson' cannot both be provided")
  public boolean isContentValid() {
    boolean hasMessage = message != null && !message.isBlank();
    boolean hasAdaptiveCard = adaptiveCardJson != null && !adaptiveCardJson.isBlank();
    return !(hasMessage && hasAdaptiveCard);
  }
}
