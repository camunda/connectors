/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack.outbound.utils;

import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.conversations.ConversationsListRequest;
import com.slack.api.methods.request.users.UsersListRequest;
import com.slack.api.methods.request.users.UsersLookupByEmailRequest;
import com.slack.api.methods.response.conversations.ConversationsListResponse;
import com.slack.api.methods.response.users.UsersListResponse;
import com.slack.api.methods.response.users.UsersLookupByEmailResponse;
import com.slack.api.model.Conversation;
import com.slack.api.model.ConversationType;
import com.slack.api.model.User;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;

public class DataLookupService {

  private DataLookupService() {}

  private static final String EMAIL_REGEX = "^.+[@].+[.].{2,4}$";

  public static List<String> convertStringToList(String string) {
    if (StringUtils.isBlank(string)) {
      return new ArrayList<>();
    }
    return Arrays.stream(string.split(",")).map(s -> s.trim()).collect(Collectors.toList());
  }

  public static boolean isEmail(final String str) {
    return str.matches(EMAIL_REGEX);
  }

  public static List<String> getUserIdsFromUsers(
      Collection<?> userList, MethodsClient methodsClient) {
    if (Objects.isNull(userList) || userList.isEmpty()) {
      return new ArrayList<>();
    }

    Collection<String> validatedUserList =
        userList.stream()
            .filter(Objects::nonNull)
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .collect(Collectors.collectingAndThen(Collectors.toList(), Optional::of))
            .filter(l -> !l.isEmpty())
            .orElseThrow(() -> new IllegalArgumentException("No user provided in a valid format"));

    List<String> emails = new ArrayList<>();
    List<String> usernames = new ArrayList<>();
    List<String> userIds = new ArrayList<>();

    validatedUserList.stream()
        .forEach(
            user -> {
              if (isEmail(user)) {
                emails.add(user);
              } else if (user.startsWith("@")) {
                usernames.add(user.substring(1));
              } else {
                userIds.add(user);
              }
            });

    List<String> idListByEmail =
        emails.stream()
            .map(
                email -> {
                  try {
                    return getUserIdByEmail(email, methodsClient);
                  } catch (Exception e) {
                    throw new RuntimeException(
                        "Unable to find user with name or email : " + email, e);
                  }
                })
            .collect(Collectors.toList());

    List<String> idListByUserName = getIdListByUserNameList(usernames, methodsClient);

    return Stream.of(idListByEmail, idListByUserName, userIds)
        .flatMap(Collection::stream)
        .collect(Collectors.toList());
  }

  public static String getUserIdByEmail(final String email, final MethodsClient methodsClient)
      throws IOException, SlackApiException {
    UsersLookupByEmailRequest lookupByEmailRequest =
        UsersLookupByEmailRequest.builder().email(email).build();

    return Optional.ofNullable(methodsClient.usersLookupByEmail(lookupByEmailRequest))
        .filter(UsersLookupByEmailResponse::isOk)
        .map(UsersLookupByEmailResponse::getUser)
        .map(User::getId)
        .orElseThrow(
            () ->
                new RuntimeException(
                    "User with email "
                        + email
                        + " not found; or unable 'users:read.email' permission"));
  }

  public static String getUserIdByUserName(String userName, MethodsClient methodsClient) {
    try {
      List<String> userIds =
          getIdListByUserNameList(Collections.singletonList(userName), methodsClient);
      return Optional.ofNullable(userIds)
          .filter(list -> !list.isEmpty())
          .map(list -> list.get(0))
          .orElseThrow(() -> new RuntimeException("Unable to find users by name: " + userName));
    } catch (RuntimeException e) {
      throw new RuntimeException("Unable to find users by name: " + userName, e);
    }
  }

  public static List<String> getIdListByUserNameList(
      List<String> userNameList, MethodsClient methodsClient) {
    if (userNameList == null || userNameList.isEmpty()) {
      return new ArrayList<>();
    }
    String nextCursor = null;
    List<String> idList = new ArrayList<>();
    do {
      UsersListRequest request = UsersListRequest.builder().limit(100).cursor(nextCursor).build();

      try {
        UsersListResponse response = methodsClient.usersList(request);
        if (response.isOk()) {
          idList.addAll(
              response.getMembers().stream()
                  .filter(user -> userNameList.contains(user.getRealName()))
                  .map(User::getId)
                  .collect(Collectors.toList()));
          nextCursor = response.getResponseMetadata().getNextCursor();
        } else {
          throw new RuntimeException("Unable to get users; message: " + response.getError());
        }
      } catch (Exception e) {
        throw new RuntimeException("Unable to find users by name", e);
      }

    } while (idList.size() < userNameList.size() && nextCursor != null && !nextCursor.isBlank());
    return idList;
  }

  public static String getChannelIdByName(String channelName, MethodsClient methodsClient) {
    String channelId = null;
    String nextCursor = null;

    do {
      List<ConversationType> allChannelType =
          Arrays.asList(
              ConversationType.PUBLIC_CHANNEL,
              ConversationType
                  .PRIVATE_CHANNEL); // we don't need IMs and MPIMs since they do not have a name
      ConversationsListRequest request =
          ConversationsListRequest.builder()
              .types(allChannelType)
              .limit(100)
              .cursor(nextCursor)
              .build();
      try {
        ConversationsListResponse response = methodsClient.conversationsList(request);
        if (response.isOk()) {
          channelId =
              response.getChannels().stream()
                  .filter(channel -> channelName.equals(channel.getName()))
                  .map(Conversation::getId)
                  .findFirst()
                  .orElse(null);
          nextCursor = response.getResponseMetadata().getNextCursor();
        } else {
          throw new RuntimeException(
              "Unable to find conversation with channel name: "
                  + channelName
                  + "; message: "
                  + response.getError());
        }
      } catch (Exception e) {
        throw new RuntimeException("Unable to find conversation with name: " + channelName, e);
      }

    } while (channelId == null && nextCursor != null && !nextCursor.isBlank());

    if (channelId == null) {
      throw new RuntimeException("Unable to find conversation with name: " + channelName);
    }

    return channelId;
  }
}
