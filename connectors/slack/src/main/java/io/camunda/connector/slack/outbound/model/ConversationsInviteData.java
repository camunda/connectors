/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack.outbound.model;

import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.conversations.ConversationsInviteRequest;
import com.slack.api.methods.response.conversations.ConversationsInviteResponse;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.Pattern;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyBinding;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyConstraints;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import io.camunda.connector.slack.outbound.SlackResponse;
import io.camunda.connector.slack.outbound.utils.DataLookupService;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

@TemplateSubType(id = "conversations.invite", label = "Invite to channel")
public record ConversationsInviteData(
    @TemplateProperty(
            label = "Invite By",
            id = "data.channelType",
            group = "invite",
            defaultValue = "channelId",
            type = TemplateProperty.PropertyType.Dropdown,
            binding = @PropertyBinding(name = "data.channelType"),
            choices = {
              @TemplateProperty.DropdownPropertyChoice(value = "channelId", label = "Channel ID"),
              @TemplateProperty.DropdownPropertyChoice(
                  value = "channelName",
                  label = "Channel name")
            },
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "method",
                    equals = "conversations.invite"))
        String channelType,
    @TemplateProperty(
            label = "Channel name",
            id = "data.channelName",
            group = "invite",
            binding = @PropertyBinding(name = "data.channelName"),
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "data.channelType",
                    equals = "channelName"),
            constraints =
                @PropertyConstraints(
                    notEmpty = true,
                    pattern =
                        @Pattern(
                            value = "^(=|([-_a-z0-9]{1,80}$))",
                            message =
                                "May contain up to 80 lowercase letters, digits, underscores, and dashes")),
            feel = FeelMode.optional)
        String channelName,
    @TemplateProperty(
            label = "Channel ID",
            id = "data.channelId",
            group = "invite",
            binding = @PropertyBinding(name = "data.channelId"),
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "data.channelType",
                    equals = "channelId"),
            constraints =
                @PropertyConstraints(
                    notEmpty = true,
                    pattern =
                        @Pattern(
                            value = "^(=|([-_a-z0-9]{1,80}$))",
                            message =
                                "May contain up to 80 lowercase letters, digits, underscores, and dashes")),
            feel = FeelMode.optional)
        String channelId,
    @TemplateProperty(
            label = "Users",
            id = "data.users",
            description =
                "Comma-separated list of users, e.g., '@user1,@user2' or '=[ \"@user1\", \"user2@company.com\"]'",
            group = "invite",
            binding = @PropertyBinding(name = "data.users"),
            feel = FeelMode.optional)
        @NotNull
        Object users)
    implements SlackRequestData {
  @Override
  public SlackResponse invoke(MethodsClient methodsClient) throws SlackApiException, IOException {
    Collection<?> userInput;
    if (users instanceof Collection<?>) {
      userInput = (Collection<?>) users;
    } else if (users instanceof String) {
      userInput = DataLookupService.convertStringToList((String) users);
    } else {
      // We accept only List or String input for users
      throw new IllegalArgumentException(
          "Invalid input type for users. Supported types are: List<String> and String");
    }

    List<String> userList = DataLookupService.getUserIdsFromUsers(userInput, methodsClient);
    ConversationsInviteRequest request =
        ConversationsInviteRequest.builder()
            .channel(
                Objects.isNull(channelId)
                    ? DataLookupService.getChannelIdByName(channelName, methodsClient)
                    : channelId)
            .users(userList)
            .build();

    ConversationsInviteResponse response = methodsClient.conversationsInvite(request);

    if (response.isOk()) {
      return new ConversationsInviteSlackResponse(response);
    } else {
      throw new RuntimeException(response.getError());
    }
  }
}
