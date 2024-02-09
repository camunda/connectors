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
import io.camunda.connector.slack.outbound.SlackResponse;
import io.camunda.connector.slack.outbound.utils.DataLookupService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

public record ConversationsInviteData(@NotBlank String channelName, @NotNull Object users)
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
            .channel(DataLookupService.getChannelIdByName(channelName, methodsClient))
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
