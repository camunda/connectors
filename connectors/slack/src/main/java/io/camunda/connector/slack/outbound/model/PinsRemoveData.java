/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack.outbound.model;

import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.pins.PinsRemoveRequest;
import com.slack.api.methods.response.pins.PinsRemoveResponse;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyBinding;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyConstraints;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import io.camunda.connector.slack.outbound.SlackResponse;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;

@TemplateSubType(id = "pins.remove", label = "Unpin Message")
public record PinsRemoveData(
    @TemplateProperty(
            label = "Channel",
            description = "Channel ID of the message to pin",
            id = "unpinMessage.channel",
            group = "unpinMessage",
            binding = @PropertyBinding(name = "data.channel"),
            constraints = @PropertyConstraints(notEmpty = true))
        @NotBlank
        String channel,
    @TemplateProperty(
            label = "Message timestamp",
            description = "Timestamp of the Slack message to unpin",
            id = "unpinMessage.timestamp",
            group = "unpinMessage",
            feel = Property.FeelMode.required,
            binding = @PropertyBinding(name = "data.timestamp"),
            constraints = @PropertyConstraints(notEmpty = true))
        @NotBlank
        String timestamp)
    implements SlackRequestData {

  @Override
  public SlackResponse invoke(MethodsClient methodsClient) throws SlackApiException, IOException {
    PinsRemoveResponse response =
        methodsClient.pinsRemove(
            PinsRemoveRequest.builder().channel(channel).timestamp(timestamp).build());

    if (response.isOk()) {
      return new PinsRemoveSlackResponse();
    }

    throw new RuntimeException("Failed to unpin message: " + response.getError());
  }
}
