/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack.outbound.model;

import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.reactions.ReactionsAddRequest;
import com.slack.api.methods.response.reactions.ReactionsAddResponse;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyBinding;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyConstraints;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import io.camunda.connector.slack.outbound.SlackResponse;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;

@TemplateSubType(id = "reactions.add", label = "Add reaction")
public record ReactionsAddData(
    @TemplateProperty(
            label = "Channel",
            tooltip = "Channel ID of the message to react to",
            id = "reaction.channel",
            group = "reaction",
            binding = @PropertyBinding(name = "data.channel"),
            constraints = @PropertyConstraints(notEmpty = true))
        @NotBlank
        String channel,
    @TemplateProperty(
            label = "Emoji name",
            placeholder = "eyes",
            id = "data.emoji",
            group = "reaction",
            binding = @PropertyBinding(name = "data.emoji"),
            constraints = @PropertyConstraints(notEmpty = true))
        @NotBlank
        String emoji,
    @TemplateProperty(
            label = "Message timestamp",
            tooltip = "Timestamp of the Slack message to react to",
            id = "data.timestamp",
            group = "reaction",
            feel = FeelMode.required,
            binding = @PropertyBinding(name = "data.timestamp"),
            constraints = @PropertyConstraints(notEmpty = true))
        @NotBlank
        String timestamp)
    implements SlackRequestData {

  @Override
  public SlackResponse invoke(MethodsClient methodsClient) throws SlackApiException, IOException {

    ReactionsAddResponse response =
        methodsClient.reactionsAdd(
            ReactionsAddRequest.builder()
                .channel(channel)
                .name(emoji)
                .timestamp(timestamp)
                .build());

    if (response.isOk()) {
      return new ReactionsAddSlackResponse();
    }

    throw new RuntimeException("Failed to add reaction: " + response.getError());
  }
}
