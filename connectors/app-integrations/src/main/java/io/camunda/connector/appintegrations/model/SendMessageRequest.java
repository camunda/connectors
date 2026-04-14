/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.appintegrations.model;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

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
    @NotEmpty
        @TemplateProperty(
            group = "message",
            label = "Message",
            description = "Text content to send.",
            type = PropertyType.Text)
        String message) {

  @AssertTrue(message = "Exactly one of 'email' or 'channelId' must be provided")
  public boolean isRecipientValid() {
    boolean hasEmail = email != null && !email.isBlank();
    boolean hasChannelId = channelId != null && !channelId.isBlank();
    return hasEmail ^ hasChannelId;
  }
}
