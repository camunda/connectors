/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack.outbound.model;

import static io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType.Dropdown;

import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.conversations.ConversationsCreateRequest;
import com.slack.api.methods.response.conversations.ConversationsCreateResponse;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.DropdownPropertyChoice;
import io.camunda.connector.generator.java.annotation.TemplateProperty.Pattern;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyBinding;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyConstraints;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import io.camunda.connector.slack.outbound.SlackResponse;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;

@TemplateSubType(id = "conversations.create", label = "Create channel")
public record ConversationsCreateData(
    @TemplateProperty(
            label = "Channel name",
            id = "data.newChannelName",
            group = "channel",
            binding = @PropertyBinding(name = "data.newChannelName"),
            constraints =
                @PropertyConstraints(
                    notEmpty = true,
                    pattern =
                        @Pattern(
                            value = "^(=|([-_a-z0-9]{1,80}$))",
                            message =
                                "May contain up to 80 lowercase letters, digits, underscores, and dashes")),
            feel = FeelMode.optional)
        String newChannelName,
    @TemplateProperty(
            id = "data.visibility",
            binding = @PropertyBinding(name = "data.visibility"),
            label = "Visibility",
            group = "channel",
            type = Dropdown,
            defaultValue = "PUBLIC",
            choices = {
              @DropdownPropertyChoice(label = "Public", value = "PUBLIC"),
              @DropdownPropertyChoice(label = "Private", value = "PRIVATE")
            })
        @NotNull
        Visibility visibility)
    implements SlackRequestData {

  public enum Visibility {
    PUBLIC,
    PRIVATE
  }

  @Override
  public SlackResponse invoke(MethodsClient methodsClient) throws SlackApiException, IOException {
    ConversationsCreateRequest request =
        ConversationsCreateRequest.builder()
            .name(newChannelName)
            .isPrivate(Visibility.PRIVATE == visibility)
            .build();

    ConversationsCreateResponse response = methodsClient.conversationsCreate(request);

    if (response.isOk()) {
      return new ConversationsCreateSlackResponse(response);
    } else {
      throw new RuntimeException(response.getError());
    }
  }
}
